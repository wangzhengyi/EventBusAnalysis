package org.greenrobot.eventbus;

import java.util.ArrayList;
import java.util.List;

/**
 * 订阅者和订阅事件信息实体类.
 */
@SuppressWarnings("unused")
public class PendingPost {
    /** PendingPost的缓冲池,防止多次new PendingPost的消耗. */
    private final static List<PendingPost> pendingPostPool = new ArrayList<>();

    /** 订阅事件信息. */
    Object event;

    /** 订阅者. */
    Subscription subscription;

    /** 队列中下一个待发送对象. */
    PendingPost next;

    private PendingPost(Object event, Subscription subscription) {
        this.event = event;
        this.subscription = subscription;
    }

    /** 如果缓冲池大小>0,则从缓冲池中获取并构造指定的PendingPost对象.否则,直接new一个PendingPost对象. */
    static PendingPost obtainPendingPost(Subscription subscription, Object event) {
        synchronized (pendingPostPool) {
            int size = pendingPostPool.size();
            if (size > 0) {
                PendingPost pendingPost = pendingPostPool.remove(size - 1);
                pendingPost.event = event;
                pendingPost.subscription = subscription;
                pendingPost.next = null;
                return pendingPost;
            }
        }

        return new PendingPost(event, subscription);
    }

    /** 释放一个PendingPost对象到缓冲池中. */
    static void releasePendingPost(PendingPost pendingPost) {
        pendingPost.event = null;
        pendingPost.subscription = null;
        pendingPost.next = null;
        synchronized (pendingPostPool) {
            if (pendingPostPool.size() < 10000) {
                pendingPostPool.add(pendingPost);
            }
        }
    }
}
