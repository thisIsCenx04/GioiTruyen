package com.storyplatform.monetization.application.port;

import java.time.Instant;
import java.util.Optional;

public interface WithdrawalCursorCodec {

    String encode(Position position);

    Optional<Position> decode(String value);

    record Position(Instant createdAt, String id) {
    }
}
