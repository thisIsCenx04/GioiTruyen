package com.storyplatform.monetization.application.port;

import java.util.Optional;

public interface ReferralCodeCodec {

    String encode(String userId);

    Optional<String> decode(String code);

    String hash(String code);
}
