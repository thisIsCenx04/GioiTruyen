package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.infrastructure.security
        .HmacReferralCodeCodec;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacReferralCodeCodecTest {

    private static final String USER_ID =
            "10000000-0000-4000-8000-000000000001";

    @Test
    void roundTripsSignedNonSequentialCode() {
        var codec = codec('a');
        String code = codec.encode(USER_ID);

        assertThat(code).hasSize(43).doesNotContain(USER_ID);
        assertThat(codec.decode(code)).contains(USER_ID);
        assertThat(codec.hash(code)).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsTamperingMalformedCodesAndOtherKeys() {
        String code = codec('a').encode(USER_ID);
        String tampered = code.substring(0, code.length() - 1)
                + (code.endsWith("A") ? "B" : "A");

        assertThat(codec('a').decode(tampered)).isEmpty();
        assertThat(codec('b').decode(code)).isEmpty();
        assertThat(codec('a').decode("not-base64!")).isEmpty();
        assertThat(codec('a').decode(null)).isEmpty();
    }

    @Test
    void requiresStrongKeyAndUuidIdentity() {
        assertThatThrownBy(() ->
                new HmacReferralCodeCodec(new byte[31])
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec('a').encode("sequential-id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static HmacReferralCodeCodec codec(char value) {
        return new HmacReferralCodeCodec(Base64.getDecoder().decode(
                String.valueOf(value).repeat(43) + "="
        ));
    }
}
