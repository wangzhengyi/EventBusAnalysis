package org.greenrobot.eventbus;

/**
 * 通过head和tail指针维护一个PendingPost的生产者-消费者模型队列.
 */
final class PendingPostQueue {
    private PendingPost head;
    private PendingPost tail;

    /** PendingPost入队,入队之后notifyAll. */
    synchronized void enqueue(PendingPost pendingPost) {
        if (pendingPost == null) {
            throw new NullPointerException("null cannot be enqueued");
        }

        if (tail != null) {
            tail.next = pendingPost;
            tail = pendingPost;
        } else if (head == null) {
            head = tail = pendingPost;
        } else {
            throw new IllegalStateException("Head present, but no tail");
        }
        notifyAll();
    }

    /** 取队头的PendingPost. */
    synchronized PendingPost poll() {
        PendingPost pendingPost = head;
        if (head != null) {
            head = head.next;
            if (head == null) {
                tail = null;
            }
        }
        return pendingPost;
    }

    /** 取队头的PendingPost,如果此时队列为空,则让出对象锁等待maxMillisToWait后再取一次队列头部元素. */
    synchronized PendingPost poll(int maxMillisToWait) throws InterruptedException {
        if (head == null) {
            wait(maxMillisToWait);
        }
        return poll();
    }
}
