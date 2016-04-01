package org.greenrobot.eventbus;

/**
 * Each event handler method has a thread mode, which determines in which thread the method is to be
 * called by EventBus.
 */
public enum ThreadMode {

    POSTING,
    MAIN,
    BACKGROUND,
    ASYNC
}
