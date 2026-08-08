package com.storyplatform.notifications.application.port;

public interface NotificationDeliveryProvider {

    String send(
            NotificationDeliveryRepository.DeliveryJob job,
            NotificationDeliveryRepository.DeliveryTarget target,
            String unsubscribeToken
    );
}
