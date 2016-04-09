package org.greenrobot.eventbus;

/** 响应函数执行时所在线程的类型. */
public enum ThreadMode {
    /** 响应函数需要运行的线程和发送响应事件的线程为同一线程. */
    POSTING,

    /** 响应函数需要运行在主线程. */
    MAIN,

    /** 响应函数需要运行的线程为后台线程,且根据优先级等进行排队,后台顺序执行. */
    BACKGROUND,

    /** 响应函数需要运行的线程为后台线程,可并发执行. */
    ASYNC
}
