package org.greenrobot.eventbus;

/**
 * Each event handler method has a thread mode, which determines in which thread the method is to be
 * called by EventBus.
 */
public enum ThreadMode {
    /**
     * Subscriber will be called in the same thread, which is posting the event.
     */
    POSTING,

    /**
     * Subscriber will be called in Android's main thread(sometimes referred to as UI thread).
     */
    MAIN,

    /**
     * Subscriber will be called in a backgroud thread.
     */
    BACKGROUND,

    /**
     * Event handler methods are called in a separate thread.
     */
    ASYNC
}
