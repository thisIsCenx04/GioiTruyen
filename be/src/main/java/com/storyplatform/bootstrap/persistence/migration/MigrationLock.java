package com.storyplatform.bootstrap.persistence.migration;

public record MigrationLock(String owner, long fencingToken) {
}
