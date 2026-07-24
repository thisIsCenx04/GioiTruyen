package com.storyplatform.unit.moderation.application;

import com.storyplatform.moderation.application
        .ExternalDonationContentDetector;
import com.storyplatform.moderation.application.contract
        .ExternalDonationContentPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalDonationContentDetectorTest {

    private final ExternalDonationContentDetector detector =
            new ExternalDonationContentDetector();

    @Test
    void blocksKnownPaymentLinksQrSolicitationAndBankAccounts() {
        assertBlocked(
                "<a href=\"https://ko-fi.com/author\">Support</a>",
                "Support the author"
        );
        assertBlocked(
                "",
                "Quét mã QR ở trên để ủng hộ nhóm dịch."
        );
        assertBlocked(
                "",
                "Ủng hộ nhóm qua số tài khoản 0123456789."
        );
        assertBlocked(
                "",
                "Donate at https://paypal.me/author."
        );
        assertBlocked(
                "<a href=\"momo://pay?target=author\">Pay</a>",
                ""
        );
        assertBlocked(
                "<a href=\"//support.ko-fi.com/author\">Support</a>",
                ""
        );
        assertBlocked("", "Donate at WWW.PAYPAL.ME/author");
    }

    @Test
    void routesAmbiguousExternalSolicitationToHumanReview() {
        var result = detector.assess(
                "<a href=\"https://author.example/support\">"
                        + "Donate here</a>",
                "Please donate"
        );

        assertThat(result.decision())
                .isEqualTo(ExternalDonationContentPolicy.Decision.REVIEW);
        assertThat(result.signals()).contains(
                ExternalDonationContentPolicy.Signal.PAYMENT_LINK_CONTEXT
        );

        var rawLink = detector.assess(
                "",
                "Support us at https://author.example/support"
        );
        assertThat(rawLink.decision())
                .isEqualTo(ExternalDonationContentPolicy.Decision.REVIEW);

        var accountQr = detector.assess(
                "",
                "QR code - bank account details"
        );
        assertThat(accountQr.decision())
                .isEqualTo(ExternalDonationContentPolicy.Decision.REVIEW);
    }

    @Test
    void allowsOrdinaryLinksAndNarrativeMentionsWithoutSolicitation() {
        assertAllowed(
                "<a href=\"https://docs.example/qr\">QR documentation</a>",
                "The museum explains how QR codes work."
        );
        assertAllowed(
                "",
                "The blood donation saved three lives."
        );
        assertAllowed(
                "",
                "A character scanned a QR code before entering the station."
        );
    }

    @Test
    void treatsMalformedLinksAsNonExecutableAndLimitsScanCost() {
        assertAllowed("<a href=\"::::\">broken</a>", "story");
        assertAllowed("<a href=\"https:///missing-host\">broken</a>", "story");
        assertAllowed(null, null);

        var result = detector.assess("", "x".repeat(1_000_001));

        assertThat(result.decision())
                .isEqualTo(ExternalDonationContentPolicy.Decision.REVIEW);
        assertThat(result.signals()).containsExactly(
                ExternalDonationContentPolicy.Signal.SCAN_LIMIT
        );
    }

    @Test
    void assessmentRejectsMissingRequiredStructure() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new ExternalDonationContentPolicy.Assessment(
                        null,
                        java.util.List.of()
                )
        ).isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new ExternalDonationContentPolicy.Assessment(
                        ExternalDonationContentPolicy.Decision.ALLOW,
                        null
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertBlocked(String html, String text) {
        assertThat(detector.assess(html, text).decision())
                .isEqualTo(ExternalDonationContentPolicy.Decision.BLOCK);
    }

    private void assertAllowed(String html, String text) {
        assertThat(detector.assess(html, text).decision())
                .isEqualTo(ExternalDonationContentPolicy.Decision.ALLOW);
    }
}
