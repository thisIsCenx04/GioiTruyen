package com.storyplatform.monetization.application.port;

import com.storyplatform.monetization.domain.PaymentEvent;

public interface PaymentEventRepository {

    boolean insertIfAbsent(PaymentEvent event);
}
