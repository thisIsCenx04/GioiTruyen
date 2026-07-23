package com.storyplatform.integration.teams;

import com.storyplatform.teams.application.TeamOperations;
import com.storyplatform.teams.infrastructure.persistence.MongoTeamDocument;
import com.storyplatform.teams.infrastructure.persistence.MongoTeamMembershipDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class TeamOwnershipIntegrationTest {

    private static final String DATABASE = "team_ownership";
    private static final String REPLICA_SET = "docker-rs";

    @Container
    static final MongoDBContainer MONGO =
            new MongoDBContainer("mongo:8.0.28").withReplicaSet();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.mongodb.uri",
                () -> MONGO.getReplicaSetUrl(DATABASE)
                        + "?directConnection=true"
                        + "&replicaSet=" + REPLICA_SET
        );
        registry.add("spring.mongodb.database", () -> DATABASE);
    }

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private TeamOperations teams;

    @Autowired
    private MongoTemplate mongo;

    @BeforeEach
    void clean() {
        mongo.dropCollection(MongoTeamDocument.COLLECTION);
        mongo.dropCollection(MongoTeamMembershipDocument.COLLECTION);
    }

    @Test
    void creatingTeamPersistsOwnerMembershipInSameTransaction() {
        var team = teams.create(
                "owner-1",
                "lam-da",
                "Lâm Dạ",
                "Nhóm dịch"
        );

        MongoTeamDocument stored = mongo.findById(
                team.id(),
                MongoTeamDocument.class
        );
        MongoTeamMembershipDocument owner = mongo.findById(
                team.id() + ":owner-1",
                MongoTeamMembershipDocument.class
        );

        assertThat(stored).isNotNull();
        assertThat(owner).isNotNull();
        assertThat(owner.role().name()).isEqualTo("OWNER");
        assertThat(owner.state().name()).isEqualTo("ACTIVE");
    }
}
