package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.TopupRequest;

import java.util.List;
import java.util.Optional;

public interface TopupRequestRepository {

    Optional<TopupRequest> findByIdempotencyKeyHash(String keyHash);

    Optional<TopupRequest> findByIdAndUserId(String id, String userId);

    List<TopupRequest> findRecentByUserId(String userId, int limit);

    TopupRequest insert(TopupRequest request);
}
