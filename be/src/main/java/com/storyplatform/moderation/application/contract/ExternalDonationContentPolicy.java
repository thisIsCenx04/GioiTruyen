package com.storyplatform.moderation.application.contract;

import java.util.List;

/**
 * Published policy contract used by content-owning modules before persistence.
 */
public interface ExternalDonationContentPolicy {

    Assessment assess(String html, String plainText);

    record Assessment(Decision decision, List<Signal> signals) {
        public Assessment {
            if (decision == null || signals == null) {
                throw new IllegalArgumentException(
                        "Donation content assessment is invalid."
                );
            }
            signals = List.copyOf(signals);
        }
    }

    enum Decision {
        ALLOW,
        REVIEW,
        BLOCK
    }

    enum Signal {
        EXTERNAL_PAYMENT_LINK,
        PAYMENT_LINK_CONTEXT,
        PAYMENT_QR_SOLICITATION,
        PAYMENT_ACCOUNT_SOLICITATION,
        SCAN_LIMIT
    }
}
