package com.storyplatform.publishing.infrastructure;

import com.storyplatform.publishing.application.PublishingScheduleOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalPublishingScheduleOperations
        implements PublishingScheduleOperations {

    private final PublishingScheduleOperations delegate;

    public TransactionalPublishingScheduleOperations(
            PublishingScheduleOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public ScheduleView schedule(
            String actorId,
            String teamId,
            String storyId,
            ScheduleCommand command
    ) {
        return delegate.schedule(actorId, teamId, storyId, command);
    }

    @Override
    @Transactional
    public ScheduleView reschedule(
            String actorId,
            String teamId,
            String storyId,
            long expectedVersion,
            RescheduleCommand command
    ) {
        return delegate.reschedule(
                actorId,
                teamId,
                storyId,
                expectedVersion,
                command
        );
    }

    @Override
    @Transactional
    public void cancel(
            String actorId,
            String teamId,
            String storyId,
            long expectedVersion
    ) {
        delegate.cancel(actorId, teamId, storyId, expectedVersion);
    }
}
