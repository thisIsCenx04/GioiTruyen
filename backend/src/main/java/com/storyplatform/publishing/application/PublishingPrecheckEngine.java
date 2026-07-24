package com.storyplatform.publishing.application;

import com.storyplatform.publishing.application.port
        .PublishingPrecheckRepository;

import java.time.Instant;
import java.util.List;

public interface PublishingPrecheckEngine {

    List<CheckResult> check(
            PublishingPrecheckRepository.ReviewEvidence evidence,
            Instant deadline
    );

    record CheckResult(
            Rule rule,
            Outcome outcome,
            String code,
            String policyVersion
    ) {
        public CheckResult {
            if (rule == null
                    || outcome == null
                    || code == null
                    || !code.matches("[A-Z][A-Z0-9_]{2,63}")
                    || policyVersion == null
                    || policyVersion.isBlank()) {
                throw new IllegalArgumentException(
                        "precheck result is invalid"
                );
            }
        }
    }

    enum Rule {
        SCHEMA,
        MEDIA,
        LINK_POLICY,
        QR_POLICY,
        SPAM
    }

    enum Outcome {
        PASS,
        FLAG,
        MANUAL,
        FAIL,
        TIMEOUT
    }
}
