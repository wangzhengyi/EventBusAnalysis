package org.greenrobot.eventbus;

/**
 * 当没有事件处理函数对事件处理时发送的EventBus内部自定义事件.
 */
public final class NoSubscriberEvent {
    public final EventBus eventBus;

    public final Object originalEvent;

    public NoSubscriberEvent(EventBus eventBus, Object originalEvent) {
        this.eventBus = eventBus;
        this.originalEvent = originalEvent;
    }
}
