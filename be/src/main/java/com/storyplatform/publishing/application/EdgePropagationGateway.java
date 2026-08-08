package com.storyplatform.publishing.application;

import java.util.List;

public interface EdgePropagationGateway {

    void invalidate(
            String idempotencyKey,
            List<String> targets
    );
}
