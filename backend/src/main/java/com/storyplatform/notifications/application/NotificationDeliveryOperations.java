package com.storyplatform.notifications.application;

public interface NotificationDeliveryOperations {

    boolean processNext(String workerId);
}
