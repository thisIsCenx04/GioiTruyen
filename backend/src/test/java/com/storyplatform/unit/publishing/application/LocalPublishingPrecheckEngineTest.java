package com.storyplatform.unit.publishing.application;

import com.storyplatform.moderation.application
        .ExternalDonationContentDetector;
import com.storyplatform.publishing.application
        .LocalPublishingPrecheckEngine;
import com.storyplatform.publishing.application.PublishingPrecheckEngine;
import com.storyplatform.publishing.application
        .PublishingPrecheckTimeoutException;
import com.storyplatform.publishing.application.port
        .PublishingPrecheckRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalPublishingPrecheckEngineTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final LocalPublishingPrecheckEngine engine =
            new LocalPublishingPrecheckEngine(
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    new ExternalDonationContentDetector()
            );

    @Test
    void safeContentAndApprovedCoverPassEveryRule() {
        var results = engine.check(
                evidence(
                        "<p>Hello <a href=\"https://example.test\">world</a></p>",
                        "Hello world",
                        "a".repeat(64),
                        approvedCover()
                ),
                NOW.plusSeconds(1)
        );

        assertThat(results).hasSize(5);
        assertThat(results).allMatch(value ->
                value.outcome() == PublishingPrecheckEngine.Outcome.PASS
        );
    }

    @Test
    void externalDonationFailsAndOtherSignalsRemainReviewable() {
        var results = engine.check(
                evidence(
                        "<p><a href=\"http://unsafe.test\">pay</a></p>",
                        "Scan QR donate aaaaaaaaaaaa",
                        "a".repeat(64),
                        null
                ),
                NOW.plusSeconds(1)
        );

        assertThat(results).anySatisfy(value -> {
            assertThat(value.rule())
                    .isEqualTo(PublishingPrecheckEngine.Rule.QR_POLICY);
            assertThat(value.outcome())
                    .isEqualTo(PublishingPrecheckEngine.Outcome.FAIL);
            assertThat(value.code())
                    .isEqualTo("EXTERNAL_DONATION_BLOCKED");
        });
        assertThat(results).filteredOn(value ->
                value.outcome() == PublishingPrecheckEngine.Outcome.FLAG
        ).extracting(PublishingPrecheckEngine.CheckResult::rule)
                .containsExactly(
                        PublishingPrecheckEngine.Rule.LINK_POLICY,
                        PublishingPrecheckEngine.Rule.SPAM
                );
    }

    @Test
    void unapprovedCoverAndMissingRevisionRequireHumanReview() {
        var incomplete = new PublishingPrecheckRepository.ReviewEvidence(
                PublishingPrecheckServiceTest.review(),
                "Story",
                "Synopsis",
                "30000000-0000-4000-8000-000000000001",
                new PublishingPrecheckRepository.MediaEvidence(
                        "TEAM",
                        "20000000-0000-4000-8000-000000000001",
                        "STORY_COVER",
                        "PROCESSING",
                        "PENDING"
                ),
                List.of()
        );

        var results = engine.check(incomplete, NOW.plusSeconds(1));

        assertThat(results).anySatisfy(value -> {
            assertThat(value.rule())
                    .isEqualTo(PublishingPrecheckEngine.Rule.SCHEMA);
            assertThat(value.outcome())
                    .isEqualTo(PublishingPrecheckEngine.Outcome.FAIL);
        });
        assertThat(results).anySatisfy(value -> {
            assertThat(value.rule())
                    .isEqualTo(PublishingPrecheckEngine.Rule.MEDIA);
            assertThat(value.outcome())
                    .isEqualTo(PublishingPrecheckEngine.Outcome.MANUAL);
        });
    }

    @Test
    void scansStoryMetadataAsPartOfFrozenReviewEvidence() {
        var safe = evidence(
                "<p>Chapter</p>",
                "Chapter",
                "a".repeat(64),
                null
        );
        var unsafe = new PublishingPrecheckRepository.ReviewEvidence(
                safe.review(),
                safe.storyTitle(),
                "Donate at https://paypal.me/author",
                safe.coverAssetId(),
                safe.cover(),
                safe.chapters()
        );

        assertThat(engine.check(unsafe, NOW.plusSeconds(1)))
                .anySatisfy(result -> {
                    assertThat(result.rule())
                            .isEqualTo(
                                    PublishingPrecheckEngine.Rule.QR_POLICY
                            );
                    assertThat(result.outcome())
                            .isEqualTo(
                                    PublishingPrecheckEngine.Outcome.FAIL
                            );
                });
    }

    @Test
    void ambiguousDonationLinkCreatesReviewSignalNotHardFailure() {
        var results = engine.check(
                evidence(
                        "<p><a href=\"https://author.example/support\">"
                                + "Donate here</a></p>",
                        "Support the author",
                        "a".repeat(64),
                        null
                ),
                NOW.plusSeconds(1)
        );

        assertThat(results).anySatisfy(result -> {
            assertThat(result.rule())
                    .isEqualTo(PublishingPrecheckEngine.Rule.QR_POLICY);
            assertThat(result.outcome())
                    .isEqualTo(PublishingPrecheckEngine.Outcome.FLAG);
            assertThat(result.code())
                    .isEqualTo("DONATION_CONTENT_REVIEW");
        });
    }

    @Test
    void expiredBudgetFallsBackThroughTimeoutSignal() {
        assertThatThrownBy(() -> engine.check(
                PublishingPrecheckServiceTest.evidence(),
                NOW
        )).isInstanceOf(PublishingPrecheckTimeoutException.class);
    }

    private static PublishingPrecheckRepository.ReviewEvidence evidence(
            String html,
            String text,
            String checksum,
            PublishingPrecheckRepository.MediaEvidence cover
    ) {
        return new PublishingPrecheckRepository.ReviewEvidence(
                PublishingPrecheckServiceTest.review(),
                "Story",
                "Synopsis",
                cover == null
                        ? null
                        : "30000000-0000-4000-8000-000000000001",
                cover,
                List.of(new PublishingPrecheckRepository.ChapterEvidence(
                        PublishingPrecheckServiceTest.review()
                                .chapters().getFirst().chapterId(),
                        html,
                        text,
                        checksum
                ))
        );
    }

    private static PublishingPrecheckRepository.MediaEvidence
            approvedCover() {
        return new PublishingPrecheckRepository.MediaEvidence(
                "TEAM",
                "20000000-0000-4000-8000-000000000001",
                "STORY_COVER",
                "READY",
                "APPROVED"
        );
    }
}
