package com.storyplatform.media.infrastructure;

import com.storyplatform.media.application.port.UploadParameterSigner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

public final class CloudinaryUploadParameterSigner
        implements UploadParameterSigner {

    private final byte[] apiSecret;

    public CloudinaryUploadParameterSigner(String apiSecret) {
        if (apiSecret == null || apiSecret.length() < 16) {
            throw new IllegalArgumentException(
                    "Cloudinary API secret must contain at least 16 characters"
            );
        }
        this.apiSecret = apiSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String sign(Map<String, String> parameters) {
        Objects.requireNonNull(parameters, "parameters");
        String canonical = parameters.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> !entry.getValue().isBlank())
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right)
                .orElseThrow(() -> new IllegalArgumentException(
                        "at least one signed parameter is required"
                ));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(apiSecret));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }
}
