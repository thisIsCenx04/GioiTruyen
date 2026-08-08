package com.storyplatform.teams.domain;

import java.util.Set;

public final class TeamPermissions {

    public static final Set<String> MEMBER_ASSIGNABLE = Set.of(
            "story:create",
            "story:edit",
            "story:submit",
            "story:publish",
            "analytics:read",
            "finance:request"
    );

    private TeamPermissions() {
    }

    public static boolean areMemberAssignable(Set<String> permissions) {
        return permissions != null
                && !permissions.isEmpty()
                && MEMBER_ASSIGNABLE.containsAll(permissions);
    }
}
