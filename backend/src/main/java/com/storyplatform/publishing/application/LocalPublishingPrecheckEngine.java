package com.storyplatform.publishing.application;

import com.storyplatform.publishing.application.port
        .PublishingPrecheckRepository;
import com.storyplatform.moderation.application.contract
        .ExternalDonationContentPolicy;
import org.jsoup.Jsoup;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class LocalPublishingPrecheckEngine
        implements PublishingPrecheckEngine {

    private static final int MAXIMUM_LINKS = 50;
    private static final long MAXIMUM_SCAN_CHARACTERS = 5_000_000;
    private static final Pattern REPEATED_CHARACTER =
            Pattern.compile("(.)\\1{11,}");

    private final Clock clock;
    private final ExternalDonationContentPolicy donationPolicy;

    public LocalPublishingPrecheckEngine(
            Clock clock,
            ExternalDonationContentPolicy donationPolicy
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.donationPolicy = Objects.requireNonNull(
                donationPolicy,
                "donationPolicy"
        );
    }

    @Override
    public List<CheckResult> check(
            PublishingPrecheckRepository.ReviewEvidence evidence,
            Instant deadline
    ) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(deadline, "deadline");
        deadline(deadline);
        List<CheckResult> results = new ArrayList<>();
        results.add(schema(evidence));
        results.add(media(evidence));
        long characters = length(evidence.storyTitle())
                + length(evidence.storySynopsis())
                + evidence.chapters().stream()
                .mapToLong(value -> (long) value.contentHtml().length()
                        + value.plainText().length())
                .sum();
        if (characters > MAXIMUM_SCAN_CHARACTERS) {
            results.add(result(
                    Rule.LINK_POLICY,
                    Outcome.MANUAL,
                    "CONTENT_SCAN_LIMIT"
            ));
            results.add(result(
                    Rule.QR_POLICY,
                    Outcome.MANUAL,
                    "CONTENT_SCAN_LIMIT"
            ));
            results.add(result(
                    Rule.SPAM,
                    Outcome.MANUAL,
                    "CONTENT_SCAN_LIMIT"
            ));
            return List.copyOf(results);
        }
        results.add(linkPolicy(evidence, deadline));
        results.add(qrPolicy(evidence, deadline));
        results.add(spam(evidence, deadline));
        return List.copyOf(results);
    }

    private static CheckResult schema(
            PublishingPrecheckRepository.ReviewEvidence evidence
    ) {
        boolean valid = !evidence.chapters().isEmpty()
                && evidence.storyTitle() != null
                && !evidence.storyTitle().isBlank()
                && evidence.storySynopsis() != null
                && evidence.chapters().size()
                == evidence.review().chapters().size()
                && evidence.chapters().stream().allMatch(value ->
                value.contentHtml() != null
                        && !value.contentHtml().isBlank()
                        && value.plainText() != null
                        && !value.plainText().isBlank()
                        && value.checksum() != null
                        && value.checksum().matches("[0-9a-f]{64}")
        );
        return result(
                Rule.SCHEMA,
                valid ? Outcome.PASS : Outcome.FAIL,
                valid ? "FROZEN_EVIDENCE_VALID" : "FROZEN_EVIDENCE_INVALID"
        );
    }

    private static CheckResult media(
            PublishingPrecheckRepository.ReviewEvidence evidence
    ) {
        if (evidence.coverAssetId() == null) {
            return result(
                    Rule.MEDIA,
                    Outcome.PASS,
                    "COVER_NOT_REQUIRED"
            );
        }
        var cover = evidence.cover();
        boolean ready = cover != null
                && "TEAM".equals(cover.ownerType())
                && evidence.review().teamId().equals(cover.ownerId())
                && "STORY_COVER".equals(cover.purpose())
                && "READY".equals(cover.state())
                && "APPROVED".equals(cover.moderationState());
        return result(
                Rule.MEDIA,
                ready ? Outcome.PASS : Outcome.MANUAL,
                ready ? "COVER_APPROVED" : "COVER_REVIEW_REQUIRED"
        );
    }

    private CheckResult linkPolicy(
            PublishingPrecheckRepository.ReviewEvidence evidence,
            Instant deadline
    ) {
        int links = 0;
        boolean unsafe = false;
        for (var chapter : evidence.chapters()) {
            deadline(deadline);
            for (var anchor : Jsoup.parseBodyFragment(
                    chapter.contentHtml()
            ).select("a[href]")) {
                links++;
                try {
                    URI uri = URI.create(anchor.attr("href"));
                    if (!"https".equalsIgnoreCase(uri.getScheme())
                            || uri.getHost() == null) {
                        unsafe = true;
                    }
                } catch (IllegalArgumentException exception) {
                    unsafe = true;
                }
            }
        }
        boolean flagged = unsafe || links > MAXIMUM_LINKS;
        return result(
                Rule.LINK_POLICY,
                flagged ? Outcome.FLAG : Outcome.PASS,
                flagged ? "EXTERNAL_LINK_REVIEW" : "LINK_POLICY_PASS"
        );
    }

    private CheckResult qrPolicy(
            PublishingPrecheckRepository.ReviewEvidence evidence,
            Instant deadline
    ) {
        ExternalDonationContentPolicy.Decision decision =
                donationPolicy.assess(
                        "",
                        Objects.toString(evidence.storyTitle(), "")
                                + "\n"
                                + Objects.toString(
                                evidence.storySynopsis(),
                                ""
                        )
                ).decision();
        for (var chapter : evidence.chapters()) {
            deadline(deadline);
            var assessed = donationPolicy.assess(
                    chapter.contentHtml(),
                    chapter.plainText()
            );
            if (assessed.decision()
                    == ExternalDonationContentPolicy.Decision.BLOCK) {
                decision = assessed.decision();
                break;
            }
            if (assessed.decision()
                    == ExternalDonationContentPolicy.Decision.REVIEW) {
                if (decision
                        == ExternalDonationContentPolicy.Decision.ALLOW) {
                    decision = assessed.decision();
                }
            }
        }
        Outcome outcome = switch (decision) {
            case ALLOW -> Outcome.PASS;
            case REVIEW -> Outcome.FLAG;
            case BLOCK -> Outcome.FAIL;
        };
        String code = switch (decision) {
            case ALLOW -> "DONATION_POLICY_PASS";
            case REVIEW -> "DONATION_CONTENT_REVIEW";
            case BLOCK -> "EXTERNAL_DONATION_BLOCKED";
        };
        return result(
                Rule.QR_POLICY,
                outcome,
                code
        );
    }

    private CheckResult spam(
            PublishingPrecheckRepository.ReviewEvidence evidence,
            Instant deadline
    ) {
        Set<String> checksums = new HashSet<>();
        boolean signal = false;
        for (var chapter : evidence.chapters()) {
            deadline(deadline);
            if (!checksums.add(chapter.checksum())
                    || REPEATED_CHARACTER.matcher(
                    chapter.plainText()
            ).find()) {
                signal = true;
                break;
            }
        }
        return result(
                Rule.SPAM,
                signal ? Outcome.FLAG : Outcome.PASS,
                signal ? "SPAM_SIGNAL" : "SPAM_POLICY_PASS"
        );
    }

    private void deadline(Instant deadline) {
        if (!clock.instant().isBefore(deadline)) {
            throw new PublishingPrecheckTimeoutException();
        }
    }

    private static long length(String value) {
        return value == null ? 0 : value.length();
    }

    private static CheckResult result(
            Rule rule,
            Outcome outcome,
            String code
    ) {
        return new CheckResult(
                rule,
                outcome,
                code,
                PublishingPrecheckService.POLICY_VERSION
        );
    }
}
