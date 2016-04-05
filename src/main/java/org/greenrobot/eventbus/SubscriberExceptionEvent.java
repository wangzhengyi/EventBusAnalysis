package org.greenrobot.eventbus;

public class SubscriberExceptionEvent {
    public final EventBus eventBus;

    public final Throwable throwable;

    public final Object causingEvent;

    public final Object causingSubscriber;

    public SubscriberExceptionEvent(EventBus eventBus, Throwable throwable, Object causingEvent,
                                    Object causingSubscriber) {
        this.eventBus = eventBus;
        this.throwable = throwable;
        this.causingEvent = causingEvent;
        this.causingSubscriber = causingSubscriber;
    }
}
