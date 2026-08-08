package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.infrastructure.VietQrPayloadFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VietQrPayloadFactoryTest {

    @Test
    void producesDynamicEmvPayloadWithAmountReferenceAndCrc() {
        var factory = new VietQrPayloadFactory(
                "970436",
                "1234567890",
                "GIOI TRUYEN"
        );

        String payload = factory.create(100_000, "GT12345678901234");

        assertThat(payload)
                .startsWith("000201010212")
                .contains("5303704")
                .contains("5406100000")
                .contains("GT12345678901234")
                .matches(".*6304[0-9A-F]{4}$");
    }

    @Test
    void rejectsUnsafeSettlementConfiguration() {
        assertThatThrownBy(() -> new VietQrPayloadFactory(
                "bank",
                "123456",
                "GIOI TRUYEN"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VietQrPayloadFactory(
                "970436",
                "123456",
                "Giới Truyện"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
