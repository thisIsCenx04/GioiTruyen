package com.storyplatform.media.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Signs Cloudinary upload parameter sets using SHA-256.
 *
 * <p>The canonical payload is built by sorting parameters alphabetically,
 * joining them as {@code key=value&...}, and appending the API secret.
 * Empty, blank, and {@code null} parameter values are omitted.
 *
 * <p>The API secret must be at least 16 characters.
 */
public final class CloudinaryUploadParameterSigner {

    private static final int MINIMUM_SECRET_LENGTH = 16;

    private final String apiSecret;

    public CloudinaryUploadParameterSigner(String apiSecret) {
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalArgumentException(
                    "apiSecret must not be null or blank"
            );
        }
        if (apiSecret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalArgumentException(
                    "apiSecret must be at least " + MINIMUM_SECRET_LENGTH
                            + " characters"
            );
        }
        this.apiSecret = apiSecret;
    }

    /**
     * Signs the given parameter map.
     *
     * @param parameters the upload parameters to sign (may contain null/blank
     *                   values which are ignored)
     * @return the lowercase hex SHA-256 signature
     * @throws IllegalArgumentException if the effective parameter set is empty
     */
    public String sign(Map<String, String> parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        // Sort alphabetically, exclude null/blank values
        TreeMap<String, String> sorted = new TreeMap<>();
        parameters.forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                sorted.put(k, v);
            }
        });
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException(
                    "parameters must contain at least one non-blank entry"
            );
        }
        StringBuilder canonical = new StringBuilder();
        sorted.forEach((k, v) -> {
            if (!canonical.isEmpty()) canonical.append("&");
            canonical.append(k).append("=").append(v);
        });
        canonical.append(apiSecret);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
