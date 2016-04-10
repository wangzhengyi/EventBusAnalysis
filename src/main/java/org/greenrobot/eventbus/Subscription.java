package org.greenrobot.eventbus;

/**
 * 订阅者信息,包含订阅者对象,订阅者中一个订阅函数.
 */
final class Subscription {
    /** 订阅者的实例化对象. */
    final Object subscriber;

    /** 订阅者的订阅函数信息. */
    final SubscriberMethod subscriberMethod;

    volatile boolean active;

    Subscription(Object subscriber, SubscriberMethod subscriberMethod) {
        this.subscriber = subscriber;
        this.subscriberMethod = subscriberMethod;
        active = true;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Subscription) {
            Subscription otherSubscription = (Subscription) other;
            return subscriber == otherSubscription.subscriber &&
                    subscriberMethod.equals(otherSubscription.subscriberMethod);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return subscriber.hashCode() + subscriberMethod.methodString.hashCode();
    }
}
