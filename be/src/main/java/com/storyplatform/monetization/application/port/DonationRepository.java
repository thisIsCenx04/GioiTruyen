package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.Donation;

import java.util.Optional;

public interface DonationRepository {

    Optional<Donation> findByIdempotencyKeyHash(String keyHash);

    Donation insert(Donation donation);
}
