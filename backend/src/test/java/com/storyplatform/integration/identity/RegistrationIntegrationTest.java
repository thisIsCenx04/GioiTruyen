package com.storyplatform.integration.identity;

import com.storyplatform.bootstrap.persistence.migration.UserIndexes;
import com.storyplatform.identity.domain.GlobalRole;
import com.storyplatform.identity.domain.UserState;
import com.storyplatform.identity.infrastructure.persistence.MongoUserAccountDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @BeforeEach
    void resetUsers() {
        mongoTemplate.dropCollection(
                MongoUserAccountDocument.COLLECTION
        );
        new UserIndexes().apply(mongoTemplate);
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
}
