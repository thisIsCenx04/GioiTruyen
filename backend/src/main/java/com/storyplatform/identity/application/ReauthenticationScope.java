package com.storyplatform.identity.application;

public enum ReauthenticationScope {
    EMAIL_CHANGE,
    MFA_FACTOR_CHANGE,
    TEAM_OWNER_CHANGE,
    TEAM_DESTINATION_CHANGE,
    MODERATION_EMERGENCY_TAKEDOWN,
    TOPUP_MANUAL_APPROVAL,
    WITHDRAWAL_APPROVAL,
    SYSTEM_CONFIG_CHANGE
}
