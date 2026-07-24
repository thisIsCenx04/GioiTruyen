package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.infrastructure
        .TransactionalLedgerOperations;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionalLedgerOperationsTest {

    @Test
    void postingBoundaryRequiresOneDatabaseTransaction() throws Exception {
        Method post = TransactionalLedgerOperations.class.getMethod(
                "post",
                LedgerOperations.Command.class
        );

        assertThat(post.getAnnotation(Transactional.class)).isNotNull();
    }
}
