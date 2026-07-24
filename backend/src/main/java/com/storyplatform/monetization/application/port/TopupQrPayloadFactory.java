package com.storyplatform.monetization.application.port;

public interface TopupQrPayloadFactory {

    String create(long amountVnd, String transferReference);
}
