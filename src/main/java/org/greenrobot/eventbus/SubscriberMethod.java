package org.greenrobot.eventbus;

import java.lang.reflect.Method;

/**
 * 订阅者事件响应函数信息.
 */
public class SubscriberMethod {
    /** 响应函数的方法类型. */
    final Method method;

    /** 函数运行所在的线程的线程类型. */
    final ThreadMode threadMode;

    /** 订阅事件的类型,也是订阅函数第一个形参的参数类型. */
    final Class<?> eventType;

    final int priority;
    final boolean sticky;

    /** Used for efficient comparison */
    String methodString;

    public SubscriberMethod(Method method, Class<?> eventType, ThreadMode threadMode, int priority,
                            boolean sticky) {
        this.method = method;
        this.threadMode = threadMode;
        this.eventType = eventType;
        this.priority = priority;
        this.sticky = sticky;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else if (other instanceof SubscriberMethod) {
            checkMethodString();
            SubscriberMethod otherSubscriberMethod = (SubscriberMethod) other;
            otherSubscriberMethod.checkMethodString();
            return methodString.equals(otherSubscriberMethod.methodString);
        } else {
            return false;
        }
    }

    /**
     * 构造methodString,构造方法:${methodClassName}#${methodName}.
     */
    @SuppressWarnings("StringBufferReplaceableByString")
    private synchronized void checkMethodString() {
        if (methodString == null) {
            StringBuilder builder = new StringBuilder(64);
            builder.append(method.getDeclaringClass().getName());
            builder.append("#").append(method.getName());
            builder.append("(").append(eventType.getName());
            methodString = builder.toString();
        }
    }

    @Override
    public int hashCode() {
        return method.hashCode();
    }
}