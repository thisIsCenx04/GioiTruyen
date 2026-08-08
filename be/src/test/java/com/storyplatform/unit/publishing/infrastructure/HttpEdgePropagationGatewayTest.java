package com.storyplatform.unit.publishing.infrastructure;

import com.storyplatform.publishing.infrastructure
        .HttpEdgePropagationGateway;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match
        .MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match
        .MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match
        .MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response
        .MockRestResponseCreators.withSuccess;

class HttpEdgePropagationGatewayTest {

    @Test
    void purgesCloudflareAndRevalidatesIsrWithEventKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://edge.test/purge"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "event-1"))
                .andRespond(withSuccess());
        server.expect(requestTo("https://web.test/revalidate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "event-1"))
                .andRespond(withSuccess());
        var gateway = new HttpEdgePropagationGateway(
                builder.build(),
                "https://edge.test/purge",
                "https://web.test/revalidate",
                "c".repeat(32),
                "i".repeat(32)
        );

        gateway.invalidate("event-1", List.of(
                "cloudflare:tag:story:one",
                "next:isr:story:one"
        ));

        server.verify();
    }

    @Test
    void rejectsInsecureEndpointsAndMissingSecrets() {
        assertThatThrownBy(() -> new HttpEdgePropagationGateway(
                RestClient.create(),
                "http://edge.test",
                "https://web.test",
                "c".repeat(32),
                "i".repeat(32)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpEdgePropagationGateway(
                RestClient.create(),
                "https://edge.test",
                "https://web.test",
                "",
                "i".repeat(32)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
