package org.greenrobot.eventbus;

import android.os.Looper;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

/**
 * EventBus is a central publish/subscribe event system for Android.
 */
public class EventBus {
    private static final Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<>();


    private final ThreadLocal<PostingThreadState> currentPostingThreadState =
            new ThreadLocal<PostingThreadState>() {
        @Override
        protected PostingThreadState initialValue() {
            return new PostingThreadState();
        }
    };

    private final boolean throwSubscriberException;
    private final boolean logSubscriberExceptions;
    private final boolean logNoSubscriberMessages;
    private final boolean sendSubscriberExceptionEvent;
    private final boolean sendNoSubscriberEvent;
    private final boolean eventInheritance;


    /** 通过volatile保证每个线程获取的都是最新的EventBus. */
    static volatile EventBus defaultInstance;

    /** 懒汉的单例模式构造EventBus. */
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

    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();
    public EventBus() {
        this(DEFAULT_BUILDER);
    }

    /** Map<订阅事件, 订阅该事件的订阅者集合>. */
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;

    /** Map<订阅者, 订阅事件集合>. */
    private final Map<Object, List<Class<?>>> typesBySubscriber;

    /** Map<订阅事件类类型,订阅事件实例对象>. */
    private final Map<Class<?>, Object> stickyEvents;

    /** 主线程Handler实现类. */
    private final HandlerPoster mainThreadPoster;

    /** 继承Runnable的异步线程处理类,将订阅函数的执行通过Executor和队列机制在后台一个一个的执行. */
    private final BackgroundPoster backgroundPoster;

    /** 继承Runnable的异步线程处理类, 与BackgroundPoster不同的是,订阅函数的执行是并发进行的. */
    private final AsyncPoster asyncPoster;

    private final int indexCount;

    /** 订阅者响应函数信息存储和查找类. */
    private final SubscriberMethodFinder subscriberMethodFinder;

    /** 用于订阅函数后台执行的线程池. */
    private final ExecutorService executorService;

    EventBus(EventBusBuilder builder) {
        subscriptionsByEventType = new HashMap<>();
        typesBySubscriber = new HashMap<>();
        stickyEvents = new ConcurrentHashMap<>();
        mainThreadPoster = new HandlerPoster(this, Looper.myLooper(), 10);
        backgroundPoster = new BackgroundPoster(this);
        asyncPoster = new AsyncPoster(this);
        indexCount = builder.subscriberInfoIndexes != null ?
                builder.subscriberInfoIndexes.size() : 0;
        subscriberMethodFinder = new SubscriberMethodFinder(builder.subscriberInfoIndexes,
                builder.strictMethodVerification, builder.ignoreGeneratedIndex);
        logSubscriberExceptions = builder.logSubscriberExceptions;
        logNoSubscriberMessages = builder.logNoSubscriberMessages;
        sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
        sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
        throwSubscriberException = builder.throwSubscriberException;
        eventInheritance = builder.eventInheritance;
        executorService = builder.executorService;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Invokes the subscriber if the subscriptions is still active.
     * @param pendingPost
     */
    void invokeSubscriber(PendingPost pendingPost) {
        Object event = pendingPost.event;
        Subscription subscription = pendingPost.subscription;
        PendingPost.releasePendingPost(pendingPost);
        if (subscription.active) {
            invokeSubscriber(subscription, event);
        }
    }

    void invokeSubscriber(Subscription subscription, Object event) {
        try {
            subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    private void handleSubscriberException(Subscription subscription, Object event, Throwable cause) {
        if (event instanceof  SubscriberExceptionEvent) {

        } else {

        }
    }

    /**
     * For ThreadLocal, much faster to set (and get multiple values).
     */
    final static class PostingThreadState {
        final List<Object> eventQueue = new ArrayList<Object>();
        boolean isPosting;
        boolean isMainThread;
        Subscription subscription;
        Object event;
        boolean canceled;
    }

    /** 订阅事件. */
    public void register(Object subscriber) {
        // 获取订阅者类的类类型.
        Class<?> subscriberClass = subscriber.getClass();
        // 通过反射机制获取订阅者全部的响应函数信息.
        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.
                findSubscriberMethods(subscriberClass);
        // 构造订阅函数-订阅事件集合 与 订阅事件-订阅函数集合
        synchronized (this) {
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                subscribe(subscriber, subscriberMethod);
            }
        }
    }

    /**
     * 构造两个集合.
     * 1. 订阅事件->订阅者集合.
     * 2. 订阅者->订阅事件集合.
     * @param subscriber 订阅者
     * @param subscriberMethod 订阅者中的响应函数
     */
    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
        Class<?> eventType = subscriberMethod.eventType;
        Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
        // 一个Event事件可能会被多个订阅者订阅,因此这里使用Map结构,存储Event事件对应的订阅者集合.
        // 此外,一个订阅者类中可能会有多个订阅函数,有几个订阅函数这里就解析成有几个订阅者.
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions == null) {
            subscriptions = new CopyOnWriteArrayList<>();
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
            if (subscriptions.contains(newSubscription)) {
                throw new EventBusException("Subscriber " + subscriber.getClass()
                        + " already registered to event " + eventType);
            }
        }

        // 按照方法优先级从高到低的顺序将订阅者加入到订阅者集合中.
        int size = subscriptions.size();
        for (int i = 0; i <= size; i ++) {
            if (i == size ||
                    subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
                subscriptions.add(i, newSubscription);
                break;
            }
        }

        // 当前订阅者订阅了哪些事件集合.
        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<>();
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        // 将订阅事件加入到当前订阅者的订阅事件集合中.
        subscribedEvents.add(eventType);

        // 如果订阅方法为sticky类型,则立即分发sticky事件.
        if (subscriberMethod.sticky) {
            // eventInheritance的作用:是否响应订阅事件的父类事件.
            if (eventInheritance) {
                Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
                for (Map.Entry<Class<?>, Object> entry : entries) {
                    Class<?> candidateEventType = entry.getKey();
                    if (eventType.isAssignableFrom(candidateEventType)) {
                        Object stickyEvent = entry.getValue();
                        checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                    }
                }
            } else {
                Object stickyEvent = stickyEvents.get(eventType);
                postToSubscription(newSubscription, stickyEvent,
                        Looper.getMainLooper() == Looper.myLooper());
            }
        }
    }

    private void checkPostStickyEventToSubscription(Subscription newSubscription, Object stickyEvent) {
        if (stickyEvent != null) {
            postToSubscription(newSubscription, stickyEvent,
                    Looper.getMainLooper() == Looper.myLooper());
        }
    }

    /** Posts the given event to the event bus. */
    public void post(Object event) {
        PostingThreadState postingState = currentPostingThreadState.get();
        List<Object> eventQueue = postingState.eventQueue;
        eventQueue.add(event);

        if (!postingState.isPosting) {
            postingState.isMainThread = Looper.getMainLooper() == Looper.myLooper();
            postingState.isPosting = true;
            if (postingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                while (!eventQueue.isEmpty()) {
                    postSingleEvent(eventQueue.remove(0), postingState);
                }
            } finally {
                postingState.isPosting = false;
                postingState.isMainThread = false;
            }
        }
    }

    private void postSingleEvent(Object event, PostingThreadState postingState) {
        Class<?> eventClass = event.getClass();
        boolean subscriptionFound = false;

        subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
        if (!subscriptionFound) {
            if (logNoSubscriberMessages) {
                Log.e("EventBus", "No subscribers registered for event " + eventClass);
            }
            // TODO
        }
    }

    private boolean postSingleEventForEventType(Object event, PostingThreadState postingState,
                                                Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);
        }

        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (Subscription subscription : subscriptions) {
                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted = false;
                try {
                    postToSubscription(subscription, event, postingState.isMainThread);
                    aborted = postingState.canceled;
                } finally {
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }
                if (aborted) {
                    break;
                }
            }
            return true;
        }
        return false;
    }

    private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        switch (subscription.subscriberMethod.threadMode) {
            case POSTING:
                invokeSubscriber(subscription, event);
                break;
            case MAIN:
                if (isMainThread) {
                    invokeSubscriber(subscription, event);
                } else {
                    mainThreadPoster.enqueue(subscription, event);
                }
                break;
            case BACKGROUND:
                if (isMainThread) {
                    backgroundPoster.enqueue(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            case ASYNC:
                asyncPoster.enqueue(subscription, event);
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " +
                        subscription.subscriberMethod.threadMode);
        }
    }

    public synchronized void unregister(Object subscriber) {
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            for (Class<?> eventType : subscribedTypes) {
                unsubscribeByEventType(subscriber, eventType);
            }
            typesBySubscriber.remove(subscriber);
        } else {
            Log.e("EventBus", "Subscriber to unregister was not registered before: "
                    + subscriber.getClass());
        }
    }

    private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
        List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions != null) {
            int size = subscriptions.size();
            for (int i = 0; i < size; i ++) {
                Subscription subscription = subscriptions.get(i);
                if (subscription.subscriber == subscriber) {
                    subscription.active = false;
                    subscriptions.remove(i);
                    i --;
                    size --;
                }
            }
        }
    }
}
