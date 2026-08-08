package com.storyplatform.identity.infrastructure;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores one-time OAuth exchange codes with a short TTL (60 seconds).
 * This prevents JWT tokens from ever appearing in browser URLs or server logs.
 */
@Component
public class OAuthExchangeStore {

    private static final long TTL_SECONDS = 60;

    private record Entry(GoogleOAuthService.OAuthResult result, String returnTo, Instant expiresAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /**
     * Stores the OAuth result and returns a one-time opaque code.
     */
    public String store(GoogleOAuthService.OAuthResult result, String returnTo) {
        evictExpired();
        String code = UUID.randomUUID().toString();
        store.put(code, new Entry(result, returnTo, Instant.now().plusSeconds(TTL_SECONDS)));
        return code;
    }

    /**
     * Retrieves and removes the OAuth result for a given code.
     * Returns null if the code is invalid or expired.
     */
    public ExchangeResult consume(String code) {
        if (code == null || code.isBlank()) return null;
        Entry entry = store.remove(code);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            return null;
        }
        return new ExchangeResult(entry.result(), entry.returnTo());
    }

    private void evictExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            if (now.isAfter(it.next().getValue().expiresAt())) {
                it.remove();
            }
        }
    }

    public record ExchangeResult(GoogleOAuthService.OAuthResult tokens, String returnTo) {}
}
