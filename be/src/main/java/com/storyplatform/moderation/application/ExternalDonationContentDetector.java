package com.storyplatform.moderation.application;

import com.storyplatform.moderation.application.contract
        .ExternalDonationContentPolicy;
import org.jsoup.Jsoup;

import java.net.URI;
import java.text.Normalizer;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ExternalDonationContentDetector
        implements ExternalDonationContentPolicy {

    private static final int MAXIMUM_CHARACTERS = 1_000_000;
    private static final Pattern RAW_URL = Pattern.compile(
            "(?iu)\\b(?:https?://|www\\.)[^\\s<>\"']{3,2048}"
    );
    private static final Pattern SOLICITATION = Pattern.compile(
            "(?iu)\\b(?:ung\\s*ho|donat(?:e|ion)|tip\\s*(?:me|us)?"
                    + "|support\\s*(?:me|us|team|author)"
                    + "|chuyen\\s*khoan|gui\\s*tien|nap\\s*tien)\\b"
    );
    private static final Pattern QR = Pattern.compile(
            "(?iu)\\b(?:ma\\s*)?qr(?:\\s*code)?\\b"
    );
    private static final Pattern QR_SOLICITATION = Pattern.compile(
            "(?iu)(?:\\b(?:scan|quet|mo|dung)\\b.{0,48}"
                    + "\\b(?:ma\\s*)?qr\\b.{0,80}"
                    + "\\b(?:ung\\s*ho|donat(?:e|ion)|tip"
                    + "|chuyen\\s*khoan)\\b"
                    + "|\\b(?:ung\\s*ho|donat(?:e|ion)|tip"
                    + "|chuyen\\s*khoan)\\b.{0,80}"
                    + "\\b(?:ma\\s*)?qr\\b)"
    );
    private static final Pattern PAYMENT_ACCOUNT = Pattern.compile(
            "(?iu)\\b(?:so\\s*tai\\s*khoan|stk|bank\\s*account"
                    + "|wallet\\s*address|dia\\s*chi\\s*vi)\\b"
    );
    private static final Set<String> PAYMENT_SCHEMES = Set.of(
            "momo",
            "zalopay",
            "vnpay",
            "bitcoin",
            "ethereum"
    );
    private static final Set<String> PAYMENT_HOSTS = Set.of(
            "paypal.me",
            "ko-fi.com",
            "buymeacoffee.com",
            "patreon.com",
            "me.momo.vn",
            "momo.vn",
            "zalopay.vn"
    );

    @Override
    public Assessment assess(String html, String plainText) {
        String safeHtml = html == null ? "" : html;
        String safeText = plainText == null ? "" : plainText;
        if ((long) safeHtml.length() + safeText.length()
                > MAXIMUM_CHARACTERS) {
            return assessment(
                    Decision.REVIEW,
                    EnumSet.of(Signal.SCAN_LIMIT)
            );
        }
        String normalized = normalize(safeText);
        EnumSet<Signal> signals = EnumSet.noneOf(Signal.class);
        boolean externalLink = false;
        var document = Jsoup.parseBodyFragment(safeHtml);
        for (var anchor : document.select("a[href]")) {
            LinkKind kind = classify(anchor.attr("href"));
            if (kind == LinkKind.PAYMENT) {
                signals.add(Signal.EXTERNAL_PAYMENT_LINK);
            } else if (kind == LinkKind.EXTERNAL) {
                externalLink = true;
                if (SOLICITATION.matcher(
                        normalize(anchor.text())
                ).find()) {
                    signals.add(Signal.PAYMENT_LINK_CONTEXT);
                }
            }
        }
        var rawUrls = RAW_URL.matcher(safeText);
        while (rawUrls.find()) {
            LinkKind kind = classify(trimPunctuation(rawUrls.group()));
            if (kind == LinkKind.PAYMENT) {
                signals.add(Signal.EXTERNAL_PAYMENT_LINK);
            } else if (kind == LinkKind.EXTERNAL) {
                externalLink = true;
            }
        }
        boolean solicitation = SOLICITATION.matcher(normalized).find();
        if (QR_SOLICITATION.matcher(normalized).find()) {
            signals.add(Signal.PAYMENT_QR_SOLICITATION);
        }
        if (solicitation
                && PAYMENT_ACCOUNT.matcher(normalized).find()) {
            signals.add(Signal.PAYMENT_ACCOUNT_SOLICITATION);
        }
        if (signals.contains(Signal.EXTERNAL_PAYMENT_LINK)
                || signals.contains(Signal.PAYMENT_QR_SOLICITATION)
                || signals.contains(
                Signal.PAYMENT_ACCOUNT_SOLICITATION
        )) {
            return assessment(Decision.BLOCK, signals);
        }
        if (signals.contains(Signal.PAYMENT_LINK_CONTEXT)
                || (externalLink && solicitation)
                || (QR.matcher(normalized).find()
                && PAYMENT_ACCOUNT.matcher(normalized).find())) {
            if (externalLink && solicitation) {
                signals.add(Signal.PAYMENT_LINK_CONTEXT);
            }
            return assessment(Decision.REVIEW, signals);
        }
        return assessment(Decision.ALLOW, signals);
    }

    private static LinkKind classify(String raw) {
        if (raw == null || raw.isBlank()) {
            return LinkKind.NONE;
        }
        try {
            String value;
            if (raw.regionMatches(true, 0, "www.", 0, 4)) {
                value = "https://" + raw;
            } else if (raw.startsWith("//")) {
                value = "https:" + raw;
            } else {
                value = raw;
            }
            URI uri = URI.create(value);
            String scheme = lower(uri.getScheme());
            if (PAYMENT_SCHEMES.contains(scheme)) {
                return LinkKind.PAYMENT;
            }
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return LinkKind.NONE;
            }
            String host = lower(uri.getHost());
            if (host != null && PAYMENT_HOSTS.stream().anyMatch(
                    candidate -> host.equals(candidate)
                            || host.endsWith("." + candidate)
            )) {
                return LinkKind.PAYMENT;
            }
            return host == null ? LinkKind.NONE : LinkKind.EXTERNAL;
        } catch (IllegalArgumentException exception) {
            return LinkKind.NONE;
        }
    }

    private static String normalize(String value) {
        String decomposed = Normalizer.normalize(
                value,
                Normalizer.Form.NFD
        ).replace('đ', 'd').replace('Đ', 'D');
        return decomposed.replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String trimPunctuation(String value) {
        return value.replaceAll("[),.;!?]+$", "");
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static Assessment assessment(
            Decision decision,
            EnumSet<Signal> signals
    ) {
        return new Assessment(decision, List.copyOf(signals));
    }

    private enum LinkKind {
        NONE,
        EXTERNAL,
        PAYMENT
    }
}
