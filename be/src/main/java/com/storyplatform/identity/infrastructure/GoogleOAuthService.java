package com.storyplatform.identity.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.storyplatform.identity.application.port.SessionTokenIssuer;
import com.storyplatform.identity.application.port.UserAccountRepository;
import com.storyplatform.identity.application.port.UserIdGenerator;
import com.storyplatform.identity.domain.EmailNormalizer;
import com.storyplatform.identity.domain.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    private static final String TOKEN_ENDPOINT =
            "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT =
            "https://www.googleapis.com/oauth2/v3/userinfo";
    /** Placeholder prefix stored in password_hash for OAuth-only accounts. */
    private static final String OAUTH_HASH_PREFIX = "GOOGLE_OAUTH:";

    private final GoogleOAuthProperties properties;
    private final UserAccountRepository users;
    private final SessionTokenIssuer sessionTokens;
    private final EmailNormalizer emailNormalizer;
    private final UserIdGenerator idGenerator;
    private final RegistrationProperties registrationProperties;
    private final RestClient restClient;
    private final Clock clock;

    public GoogleOAuthService(
            GoogleOAuthProperties properties,
            UserAccountRepository users,
            SessionTokenIssuer sessionTokens,
            EmailNormalizer emailNormalizer,
            UserIdGenerator idGenerator,
            RegistrationProperties registrationProperties
    ) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.users = Objects.requireNonNull(users, "users");
        this.sessionTokens = Objects.requireNonNull(sessionTokens, "sessionTokens");
        this.emailNormalizer = Objects.requireNonNull(emailNormalizer, "emailNormalizer");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.registrationProperties = Objects.requireNonNull(registrationProperties, "registrationProperties");
        this.restClient = RestClient.builder().build();
        this.clock = Clock.systemUTC();
    }

    /**
     * Build the Google OAuth2 authorization URL for redirecting the user.
     *
     * @param state  CSRF-protection state value stored in session
     * @param returnTo  URL to return after login (encoded in state or param)
     */
    public String buildAuthorizationUrl(String state, String returnTo) {
        return buildAuthorizationUrl(state, returnTo, properties.redirectUri());
    }

    public String buildAuthorizationUrl(String state, String returnTo, String redirectUri) {
        String effectiveRedirectUri = (redirectUri != null && !redirectUri.isBlank())
                ? redirectUri : properties.redirectUri();
        String scope = "openid email profile";
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + properties.clientId()
                + "&redirect_uri=" + encode(effectiveRedirectUri)
                + "&response_type=code"
                + "&scope=" + encode(scope)
                + "&state=" + encode(state)
                + "&access_type=offline"
                + "&prompt=select_account";
    }

    /**
     * Exchange authorization code → tokens → upsert user → issue session.
     *
     * @return issued access+refresh tokens
     */
    @Transactional
    public OAuthResult exchangeAndLogin(String code) {
        return exchangeAndLogin(code, properties.redirectUri());
    }

    @Transactional
    public OAuthResult exchangeAndLogin(String code, String redirectUri) {
        // 1. Exchange code for tokens
        GoogleTokenResponse tokenResponse = exchangeCode(code, redirectUri);

        // 2. Fetch user profile
        GoogleUserInfo userInfo = fetchUserInfo(tokenResponse.accessToken());

        if (userInfo.email() == null || userInfo.email().isBlank()) {
            throw new IllegalStateException("Google account has no verified email.");
        }

        // 3. Normalise email
        String normalizedEmail = emailNormalizer.normalize(userInfo.email());

        // 4. Find or create UserAccount
        Instant now = clock.instant();
        UserAccount account = users.findByEmail(normalizedEmail)
                .orElseGet(() -> createOAuthUser(normalizedEmail, userInfo.sub(), now));

        // 5. Issue session tokens via existing SessionTokenIssuer
        var tokens = sessionTokens.issue(account);

        return new OAuthResult(tokens.accessToken(), tokens.refreshToken(), tokens.accessTokenExpiresInSeconds());
    }

    private UserAccount createOAuthUser(String normalizedEmail, String googleSub, Instant now) {
        UserAccount newAccount = UserAccount.active(
                idGenerator.nextId(),
                normalizedEmail,
                OAUTH_HASH_PREFIX + googleSub,   // placeholder; cannot login with password
                registrationProperties.currentConsentVersion(),
                now
        );
        boolean saved = users.saveIfEmailAvailable(newAccount);
        if (!saved) {
            // Race condition – try to load again
            return users.findByEmail(normalizedEmail)
                    .orElseThrow(() -> new IllegalStateException(
                            "Failed to create or find OAuth user: " + normalizedEmail));
        }
        log.info("Created new account via Google OAuth for email={}", normalizedEmail);
        return newAccount;
    }

    private GoogleTokenResponse exchangeCode(String code, String redirectUri) {
        String effectiveRedirectUri = (redirectUri != null && !redirectUri.isBlank())
                ? redirectUri : properties.redirectUri();
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", properties.clientId());
        body.add("client_secret", properties.clientSecret());
        body.add("redirect_uri", effectiveRedirectUri);
        body.add("grant_type", "authorization_code");

        return restClient.post()
                .uri(TOKEN_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(GoogleTokenResponse.class);
    }

    private GoogleUserInfo fetchUserInfo(String accessToken) {
        return restClient.get()
                .uri(USERINFO_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GoogleUserInfo.class);
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    /** Result returned after successful OAuth login. */
    public record OAuthResult(String accessToken, String refreshToken, long expiresInSeconds) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoogleTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoogleUserInfo(
            @JsonProperty("sub") String sub,
            @JsonProperty("email") String email,
            @JsonProperty("email_verified") Boolean emailVerified,
            @JsonProperty("name") String name,
            @JsonProperty("picture") String picture
    ) {}
}
