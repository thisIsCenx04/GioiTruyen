package com.storyplatform.identity.infrastructure.security;

import com.storyplatform.identity.application.port.LoginRiskLimiter;
import com.storyplatform.identity.application.LoginRiskUnavailableException;
import com.storyplatform.identity.infrastructure.LoginRiskProperties;
import com.storyplatform.shared.cache.RedisKey;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.cache.RedisNamespace;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public final class RedisLoginRiskLimiter implements LoginRiskLimiter {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final DefaultRedisScript<Long> ALLOW_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local email = tonumber(redis.call('GET', KEYS[1]) or '0')
                    local address = tonumber(redis.call('GET', KEYS[2]) or '0')
                    if email >= tonumber(ARGV[1])
                       or address >= tonumber(ARGV[2]) then
                      return 0
                    end
                    return 1
                    """,
                    Long.class
            );
    private static final DefaultRedisScript<Long> FAILURE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local email = redis.call('INCR', KEYS[1])
                    if email == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
                    local address = redis.call('INCR', KEYS[2])
                    if address == 1 then redis.call('PEXPIRE', KEYS[2], ARGV[1]) end
                    return email
                    """,
                    Long.class
            );

    private final StringRedisTemplate redis;
    private final RedisKeyFactory keys;
    private final LoginRiskProperties properties;
    private final SecretKeySpec fingerprintKey;

    public RedisLoginRiskLimiter(
            StringRedisTemplate redis,
            RedisKeyFactory keys,
            LoginRiskProperties properties,
            byte[] fingerprintKey
    ) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.properties = Objects.requireNonNull(properties, "properties");
        if (fingerprintKey == null || fingerprintKey.length < 32) {
            throw new IllegalArgumentException(
                    "Login risk HMAC key must contain at least 32 bytes"
            );
        }
        this.fingerprintKey = new SecretKeySpec(
                fingerprintKey.clone(),
                HMAC_ALGORITHM
        );
    }

    @Override
    public boolean allow(String email, String address) {
        try {
            List<RedisKey> riskKeys = riskKeys(email, address);
            Long result = redis.execute(
                    ALLOW_SCRIPT,
                    rawKeys(riskKeys),
                    Integer.toString(properties.maxEmailFailures()),
                    Integer.toString(properties.maxAddressFailures())
            );
            return Long.valueOf(1).equals(result);
        } catch (RuntimeException exception) {
            throw new LoginRiskUnavailableException(exception);
        }
    }

    @Override
    public long retryAfterSeconds() {
        return properties.window().toSeconds();
    }

    @Override
    public void recordFailure(String email, String address) {
        try {
            redis.execute(
                    FAILURE_SCRIPT,
                    rawKeys(riskKeys(email, address)),
                    Long.toString(properties.window().toMillis())
            );
        } catch (RuntimeException exception) {
            throw new LoginRiskUnavailableException(exception);
        }
    }

    @Override
    public void recordSuccess(String email, String address) {
        try {
            redis.delete(riskKeys(email, address).getFirst().value());
        } catch (RuntimeException exception) {
            throw new LoginRiskUnavailableException(exception);
        }
    }

    private List<RedisKey> riskKeys(String email, String address) {
        return List.of(
                keys.create(
                        RedisNamespace.RATE_LIMIT,
                        "login",
                        "email",
                        fingerprint(email)
                ),
                keys.create(
                        RedisNamespace.RATE_LIMIT,
                        "login",
                        "address",
                        fingerprint(address)
                )
        );
    }

    private String fingerprint(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(fingerprintKey);
            byte[] digest = mac.doFinal(
                    Objects.toString(value, "")
                            .getBytes(StandardCharsets.UTF_8)
            );
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint login risk input",
                    exception
            );
        }
    }

    private static List<String> rawKeys(List<RedisKey> keys) {
        return keys.stream().map(RedisKey::value).toList();
    }
}
