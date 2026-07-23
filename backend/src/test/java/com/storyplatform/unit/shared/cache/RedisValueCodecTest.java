package com.storyplatform.unit.shared.cache;

import com.storyplatform.shared.cache.RedisValueCodec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisValueCodecTest {

    private final RedisValueCodec codec = new RedisValueCodec(
            new ObjectMapper()
    );

    @Test
    void roundTripsJsonWithoutNativeJavaSerialization() {
        CachedValue value = new CachedValue("story-42", 7);

        String encoded = codec.encode(value);

        assertThat(encoded).startsWith("{");
        assertThat(encoded).doesNotContain("aced0005");
        assertThat(codec.decode(encoded, CachedValue.class))
                .isEqualTo(value);
    }

    @Test
    void rejectsMalformedPayloadAndNullValues() {
        assertThatThrownBy(() -> codec.decode("{broken", CachedValue.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Redis value could not be decoded");
        assertThatThrownBy(() -> codec.encode(null))
                .isInstanceOf(NullPointerException.class);
    }

    private record CachedValue(String storyId, int revision) {
    }
}
