package com.storyplatform.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.identity.google-oauth} block. Client id and secret are
 * blank in environments where Google sign-in is not set up; {@link #isConfigured()}
 * lets the flow degrade to a readable error instead of a failed token exchange.
 */
@ConfigurationProperties(prefix = "app.identity.google-oauth")
public class GoogleOAuthProperties {

    private String clientId = "";
    private String clientSecret = "";
    private String redirectUri = "";
    private String frontendReturnBase = "";

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getFrontendReturnBase() {
        return frontendReturnBase;
    }

    public void setFrontendReturnBase(String frontendReturnBase) {
        this.frontendReturnBase = frontendReturnBase;
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }
}
