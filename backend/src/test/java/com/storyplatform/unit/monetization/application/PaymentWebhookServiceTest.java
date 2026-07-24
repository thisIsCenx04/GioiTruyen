package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.PaymentWebhookException;
import com.storyplatform.monetization.application.PaymentWebhookOperations;
import com.storyplatform.monetization.application.PaymentWebhookService;
import com.storyplatform.monetization.application.port.PaymentEventDecoder;
import com.storyplatform.monetization.application.port.PaymentEventRepository;
import com.storyplatform.monetization.application.port.PaymentWebhookVerifier;
import com.storyplatform.monetization.domain.PaymentEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentWebhookServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T00:00:00Z");
    private static final byte[] BODY =
            "{\"eventType\":\"PAYMENT_RECEIVED\"}"
                    .getBytes(StandardCharsets.UTF_8);
    private final PaymentWebhookVerifier verifier =
            mock(PaymentWebhookVerifier.class);
    private final PaymentEventDecoder decoder =
            mock(PaymentEventDecoder.class);
    private final PaymentEventRepository repository =
            mock(PaymentEventRepository.class);
    private final PaymentWebhookService service =
            new PaymentWebhookService(
                    verifier,
                    decoder,
                    repository,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    () -> UUID.fromString(
                            "10000000-0000-4000-8000-000000000001"
                    )
            );

    @Test
    void verifiesBeforeDecodeAndStoresImmutableEvidence() {
        when(verifier.verify("local-bank", BODY, "timestamp", "signature"))
                .thenReturn(true);
        when(decoder.decode(BODY)).thenReturn(decoded());
        when(repository.insertIfAbsent(any())).thenReturn(true);
        ArgumentCaptor<PaymentEvent> event =
                ArgumentCaptor.forClass(PaymentEvent.class);

        assertThat(service.accept(
                "local-bank", BODY, "timestamp", "signature"
        )).isEqualTo(PaymentWebhookOperations.Result.ACCEPTED);

        verify(repository).insertIfAbsent(event.capture());
        assertThat(event.getValue().payloadHash()).hasSize(64);
        assertThat(event.getValue().rawPayload())
                .isEqualTo(new String(BODY, StandardCharsets.UTF_8));
        assertThat(event.getValue().status())
                .isEqualTo(PaymentEvent.Status.RECEIVED);
    }

    @Test
    void rejectsInvalidSignatureBeforeParsing() {
        assertThatThrownBy(() -> service.accept(
                "local-bank", BODY, "timestamp", "signature"
        )).isInstanceOf(PaymentWebhookException.class);
        verify(decoder, never()).decode(any());
        verify(repository, never()).insertIfAbsent(any());
    }

    @Test
    void reportsProviderEventOrBankReferenceReplayAsDuplicate() {
        when(verifier.verify("local-bank", BODY, "timestamp", "signature"))
                .thenReturn(true);
        when(decoder.decode(BODY)).thenReturn(decoded());

        assertThat(service.accept(
                "local-bank", BODY, "timestamp", "signature"
        )).isEqualTo(PaymentWebhookOperations.Result.DUPLICATE);
    }

    private static PaymentEventDecoder.DecodedPayment decoded() {
        return new PaymentEventDecoder.DecodedPayment(
                "event-1",
                "bank-reference-1",
                100_000,
                "GT12345678901234",
                NOW.minusSeconds(30)
        );
    }
}
