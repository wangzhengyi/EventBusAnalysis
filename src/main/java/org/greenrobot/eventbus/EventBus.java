package org.greenrobot.eventbus;

/**
 * Created by wzy on 16-4-1.
 */
public class EventBus {
    static volatile EventBus defaultInstance;

    public static EventBus getDefault() {
        if (defaultInstance == null) {
            synchronized (EventBus.class) {
                if (defaultInstance == null) {
                    defaultInstance = new EventBus();
                }
            }
        }
        return defaultInstance;
    }
}
