package com.storyplatform.integration.identity;

import com.storyplatform.bootstrap.persistence.migration.UserIndexes;
import com.storyplatform.bootstrap.persistence.migration
        .EmailVerificationIndexes;
import com.storyplatform.bootstrap.persistence.migration
        .RefreshSessionIndexes;
import com.storyplatform.bootstrap.persistence.migration
        .ReauthenticationGrantIndexes;
import com.storyplatform.identity.application.port
        .RefreshTokenFamilyRepository;
import com.storyplatform.identity.application.port
        .ReauthenticationGrantRepository;
import com.storyplatform.identity.application.ReauthenticationScope;
import com.storyplatform.identity.application.port.VerificationTokenCodec;
import com.storyplatform.identity.application.port.LoginRiskLimiter;
import com.storyplatform.identity.application.port.PasswordHasher;
import com.storyplatform.identity.domain.GlobalRole;
import com.storyplatform.identity.domain.UserState;
import com.storyplatform.identity.infrastructure.persistence.MongoUserAccountDocument;
import com.storyplatform.identity.infrastructure.persistence
        .MongoEmailVerificationDocument;
import com.storyplatform.identity.infrastructure.persistence
        .MongoRefreshTokenFamilyDocument;
import com.storyplatform.identity.infrastructure.persistence
        .MongoRefreshTokenFamilyRepository;
import com.storyplatform.identity.infrastructure.persistence
        .MongoReauthenticationGrantDocument;
import com.storyplatform.identity.infrastructure.persistence
        .MongoReauthenticationGrantRepository;
import com.storyplatform.identity.domain.RefreshTokenFamily;
import com.storyplatform.shared.events.persistence.OutboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RegistrationIntegrationTest {

    private static final String DATABASE = "registration_test";
    private static final String REPLICA_SET = "docker-rs";

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer("mongo:8.0.28").withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.mongodb.uri",
                () -> MONGO.getReplicaSetUrl(DATABASE)
                        + "?directConnection=true"
                        + "&replicaSet=" + REPLICA_SET
        );
        registry.add("spring.mongodb.database", () -> DATABASE);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private VerificationTokenCodec tokenCodec;

    @Autowired
    private PasswordHasher passwordHasher;

    @MockitoBean
    private LoginRiskLimiter loginRiskLimiter;

    @Autowired
    private MongoRefreshTokenFamilyRepository refreshFamilies;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private MongoReauthenticationGrantRepository reauthenticationGrants;

    @BeforeEach
    void resetUsers() {
        when(loginRiskLimiter.allow(anyString(), anyString()))
                .thenReturn(true);
        mongoTemplate.dropCollection(
                MongoUserAccountDocument.COLLECTION
        );
        new UserIndexes().apply(mongoTemplate);
        mongoTemplate.dropCollection(
                MongoEmailVerificationDocument.COLLECTION
        );
        mongoTemplate.dropCollection(OutboxMessage.COLLECTION);
        new EmailVerificationIndexes().apply(mongoTemplate);
        mongoTemplate.dropCollection(
                MongoRefreshTokenFamilyDocument.COLLECTION
        );
        new RefreshSessionIndexes().apply(mongoTemplate);
        mongoTemplate.dropCollection(
                MongoReauthenticationGrantDocument.COLLECTION
        );
        new ReauthenticationGrantIndexes().apply(mongoTemplate);
    }

    @Test
    void activeAccountCanLoginRotateAndRejectRefreshReplay()
            throws Exception {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        mongoTemplate.insert(new MongoUserAccountDocument(
                "active-user",
                "reader@example.com",
                passwordHasher.hash("correct horse battery staple"),
                Set.of(GlobalRole.USER),
                UserState.ACTIVE,
                1,
                "2026-07-24",
                now,
                now,
                now,
                0L
        ));

        var login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "Reader@Example.com",
                                  "password": "correct horse battery staple"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.accessToken").value(
                        matchesPattern(
                                "^[A-Za-z0-9_-]+\\."
                                        + "[A-Za-z0-9_-]+\\."
                                        + "[A-Za-z0-9_-]+$"
                        )
                ))
                .andExpect(jsonPath("$.refreshToken")
                        .value(matchesPattern("^[A-Za-z0-9_-]{43}$")))
                .andReturn();
        String originalRefresh = objectMapper.readTree(
                login.getResponse().getContentAsString()
        ).get("refreshToken").asText();

        var refresh = mockMvc.perform(refreshRequest(originalRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken")
                        .value(not(originalRefresh)))
                .andReturn();
        String rotatedRefresh = objectMapper.readTree(
                refresh.getResponse().getContentAsString()
        ).get("refreshToken").asText();
        assertThat(rotatedRefresh).isNotEqualTo(originalRefresh);

        mockMvc.perform(refreshRequest(originalRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void concurrentRefreshReplayRevokesEntireFamily() throws Exception {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        refreshFamilies.create(RefreshTokenFamily.active(
                "family-1",
                "user-1",
                1,
                "current-hash",
                now.plusSeconds(3600),
                now
        ));
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<RefreshTokenFamilyRepository.RotationResult>
                first = rotateAsync(start, "next-hash-1", now);
        CompletableFuture<RefreshTokenFamilyRepository.RotationResult>
                second = rotateAsync(start, "next-hash-2", now);

        start.countDown();
        List<RefreshTokenFamilyRepository.RotationStatus> statuses =
                List.of(first.get().status(), second.get().status());

        assertThat(statuses).containsExactlyInAnyOrder(
                RefreshTokenFamilyRepository.RotationStatus.ROTATED,
                RefreshTokenFamilyRepository.RotationStatus.REUSE_DETECTED
        );
        MongoRefreshTokenFamilyDocument family = mongoTemplate.findById(
                "family-1",
                MongoRefreshTokenFamilyDocument.class
        );
        assertThat(family.revokedAt()).isNotNull();
        assertThat(family.revokeReason())
                .isEqualTo(
                        MongoRefreshTokenFamilyRepository.REUSE_REASON
                );
    }

    @Test
    void userCanListRevokeRemoteAllAndCurrentSessions()
            throws Exception {
        insertActiveUser();
        mockMvc.perform(loginRequest()).andExpect(status().isOk());
        var secondLogin = mockMvc.perform(loginRequest())
                .andExpect(status().isOk())
                .andReturn();
        String access = objectMapper.readTree(
                secondLogin.getResponse().getContentAsString()
        ).get("accessToken").asText();
        Jwt decodedAccess = jwtDecoder.decode(access);
        String currentSessionId = decodedAccess.getClaimAsString("sid");
        String remoteSessionId = refreshFamilies.findActiveByUser(
                        "active-user",
                        decodedAccess.getIssuedAt().minusSeconds(1)
                ).stream()
                .map(RefreshTokenFamilyRepository.SessionRecord::id)
                .filter(id -> !id.equals(currentSessionId))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/auth/sessions")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.sessions[*].current").value(
                        containsInAnyOrder(true, false)
                ));

        mockMvc.perform(delete("/auth/sessions/{id}", remoteSessionId)
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/auth/sessions")
                        .header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.sessions.length()").value(1));

        mockMvc.perform(delete("/auth/sessions")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/auth/sessions")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());

        var thirdLogin = mockMvc.perform(loginRequest())
                .andExpect(status().isOk())
                .andReturn();
        String thirdAccess = objectMapper.readTree(
                thirdLogin.getResponse().getContentAsString()
        ).get("accessToken").asText();
        mockMvc.perform(post("/auth/logout")
                        .header(
                                "Authorization",
                                "Bearer " + thirdAccess
                        ))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/auth/sessions")
                        .header(
                                "Authorization",
                                "Bearer " + thirdAccess
                        ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void privilegedAccountCannotLoginWithoutMfaEnrollment()
            throws Exception {
        insertActiveUser(GlobalRole.ADMIN);

        mockMvc.perform(loginRequest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("MFA_ENROLLMENT_REQUIRED"));
        assertThat(mongoTemplate.findAll(
                MongoRefreshTokenFamilyDocument.class
        )).isEmpty();
    }

    @Test
    void scopedReauthenticationGrantIsSingleUseAndExactBound() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        reauthenticationGrants.save(
                new ReauthenticationGrantRepository.Grant(
                        "grant-1",
                        "token-hash",
                        "admin-1",
                        ReauthenticationScope.TOPUP_MANUAL_APPROVAL,
                        "topup",
                        "topup-1",
                        now.plusSeconds(300),
                        null,
                        now
                )
        );

        assertThat(reauthenticationGrants.consume(
                "token-hash",
                "admin-1",
                ReauthenticationScope.WITHDRAWAL_APPROVAL,
                "topup",
                "topup-1",
                now
        )).isFalse();
        assertThat(reauthenticationGrants.consume(
                "token-hash",
                "admin-1",
                ReauthenticationScope.TOPUP_MANUAL_APPROVAL,
                "topup",
                "topup-1",
                now
        )).isTrue();
        assertThat(reauthenticationGrants.consume(
                "token-hash",
                "admin-1",
                ReauthenticationScope.TOPUP_MANUAL_APPROVAL,
                "topup",
                "topup-1",
                now.plusSeconds(1)
        )).isFalse();
    }

    @Test
    void verificationTokenIsSingleUseAndActivatesAccount()
            throws Exception {
        mockMvc.perform(request(
                        "reader@example.com",
                        "correct horse battery staple"
                ))
                .andExpect(status().isAccepted());

        MongoEmailVerificationDocument verification =
                mongoTemplate.findAll(
                        MongoEmailVerificationDocument.class
                ).getFirst();
        String token = tokenCodec.tokenForDelivery(
                verification.id(),
                verification.userId(),
                verification.expiresAt()
        );

        mockMvc.perform(verificationRequest(token))
                .andExpect(status().isNoContent());
        assertThat(mongoTemplate.findById(
                verification.userId(),
                MongoUserAccountDocument.class
        ).state()).isEqualTo(UserState.ACTIVE);
        assertThat(mongoTemplate.findById(
                verification.id(),
                MongoEmailVerificationDocument.class
        ).consumedAt()).isNotNull();

        mockMvc.perform(verificationRequest(token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    void registrationPersistsPendingAccountWithArgon2idHash()
            throws Exception {
        String rawPassword = "correct horse battery staple";

        mockMvc.perform(request(
                        " Reader@Example.COM ",
                        rawPassword
                ))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status")
                        .value("PENDING_VERIFICATION"))
                .andExpect(content().string(not(
                        containsString("reader@example.com")
                )));

        List<MongoUserAccountDocument> users = mongoTemplate.findAll(
                MongoUserAccountDocument.class
        );
        assertThat(users).singleElement().satisfies(user -> {
            assertThat(user.emailNormalized())
                    .isEqualTo("reader@example.com");
            assertThat(user.passwordHash()).startsWith("$argon2id$");
            assertThat(user.passwordHash()).doesNotContain(rawPassword);
            assertThat(user.state())
                    .isEqualTo(UserState.PENDING_EMAIL_VERIFICATION);
            assertThat(user.globalRoles())
                    .containsExactly(GlobalRole.USER);
            assertThat(user.securityVersion()).isEqualTo(1);
            assertThat(user.acceptedConsentVersion())
                    .isEqualTo("2026-07-24");
        });
    }

    @Test
    void duplicateRegistrationReturnsIdenticalGenericResponse()
            throws Exception {
        String firstResponse = mockMvc.perform(request(
                        "reader@example.com",
                        "correct horse battery staple"
                ))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        MongoUserAccountDocument first = mongoTemplate.findAll(
                MongoUserAccountDocument.class
        ).getFirst();

        String duplicateResponse = mockMvc.perform(request(
                        "READER@example.com",
                        "another valid passphrase"
                ))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(duplicateResponse).isEqualTo(firstResponse);
        assertThat(mongoTemplate.findAll(
                MongoUserAccountDocument.class
        )).singleElement().satisfies(user ->
                assertThat(user.passwordHash())
                        .isEqualTo(first.passwordHash())
        );
    }

    private static org.springframework.test.web.servlet
            .request.MockHttpServletRequestBuilder request(
                    String email,
                    String password
            ) {
        return post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s",
                          "acceptedTerms": true,
                          "consentVersion": "2026-07-24"
                        }
                        """.formatted(email, password));
    }

    private static org.springframework.test.web.servlet
            .request.MockHttpServletRequestBuilder refreshRequest(
                    String token
            ) {
        return post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}");
    }

    private static org.springframework.test.web.servlet
            .request.MockHttpServletRequestBuilder verificationRequest(
                    String token
            ) {
        return post("/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}");
    }

    private CompletableFuture<
            RefreshTokenFamilyRepository.RotationResult> rotateAsync(
                    CountDownLatch start,
                    String nextHash,
                    Instant now
            ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                start.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return refreshFamilies.rotate(
                    "current-hash",
                    nextHash,
                    now.plusSeconds(1),
                    500
            );
        });
    }

    private void insertActiveUser() {
        insertActiveUser(GlobalRole.USER);
    }

    private void insertActiveUser(GlobalRole role) {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        mongoTemplate.insert(new MongoUserAccountDocument(
                "active-user",
                "reader@example.com",
                passwordHasher.hash("correct horse battery staple"),
                Set.of(role),
                UserState.ACTIVE,
                1,
                "2026-07-24",
                now,
                now,
                now,
                0L
        ));
    }

    private static org.springframework.test.web.servlet
            .request.MockHttpServletRequestBuilder loginRequest() {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "reader@example.com",
                          "password": "correct horse battery staple"
                        }
                        """);
    }
}
