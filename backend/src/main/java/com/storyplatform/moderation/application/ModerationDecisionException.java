package com.storyplatform.moderation.application;

import java.util.Objects;

public final class ModerationDecisionException extends RuntimeException {

    private final String code;
    private final Kind kind;

    public ModerationDecisionException(
            String code,
            String message,
            Kind kind
    ) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public String code() {
        return code;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        INVALID,
        CONFLICT
    }
}
