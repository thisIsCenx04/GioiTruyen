package com.storyplatform.monetization.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Clears out top-up requests nobody followed through on.
 *
 * <p>A reader who opens the payment screen and walks away leaves a row behind.
 * Without this the admin queue slowly fills with transfers that were never made,
 * and the genuine ones get harder to spot.
 */
@Component
public class StaleTopupWorker {

    private static final Logger log = LoggerFactory.getLogger(StaleTopupWorker.class);

    private final TopupService topupService;

    public StaleTopupWorker(TopupService topupService) {
        this.topupService = topupService;
    }

    @Scheduled(fixedDelayString = "${app.topups.cleanup-interval-ms:900000}", initialDelay = 60_000)
    public void expireStaleTopups() {
        try {
            int cancelled = topupService.expireStaleTopups();
            if (cancelled > 0) {
                log.info("Cancelled {} stale top-up requests", cancelled);
            }
        } catch (RuntimeException failure) {
            // A failed sweep must not take the scheduler down; the next run retries.
            log.warn("Could not expire stale top-ups", failure);
        }
    }
}
