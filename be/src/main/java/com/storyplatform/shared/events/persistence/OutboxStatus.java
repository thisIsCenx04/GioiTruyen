package com.storyplatform.shared.events.persistence;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    DEAD_LETTER
}
