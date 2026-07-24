package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.infrastructure
        .SecureTopupReferenceSupplier;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecureTopupReferenceSupplierTest {

    @Test
    void generatesOpaqueHumanReadableReferences() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(32)).thenReturn(1, 2, 3, 4);

        String reference = new SecureTopupReferenceSupplier(random).get();

        assertThat(reference)
                .startsWith("GT")
                .hasSize(16)
                .matches("GT[0-9A-Z]{14}");
    }
}
