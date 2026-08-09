package com.storyplatform.shared.api;

public final class ApiRequestLimits {

    public static final int MAX_JSON_DEPTH = 32;
    public static final int MAX_JSON_FIELD_NAME_LENGTH = 128;
    public static final int MAX_JSON_NUMBER_LENGTH = 128;
    public static final int MAX_JSON_STRING_LENGTH = 524_288;
    public static final long MAX_JSON_TOKENS = 100_000;
    public static final int MAX_REQUEST_BODY_BYTES = 1_048_576;

    private ApiRequestLimits() {
    }

}
