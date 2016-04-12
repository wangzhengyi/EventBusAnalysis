package org.greenrobot.eventbus;

import android.os.Looper;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

/** EventBus,Android平台的订阅,发布总线机制. */
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class EventBus {
    private static final Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<>();

    /** 存储当前线程的PostingThreadState对象. */
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

    private void handleSubscriberException(Subscription subscription, Object event, Throwable cause) {
        // do nothing
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

    /** 当前线程的事件分发类. */
    final static class PostingThreadState {
        /** 当前线程的发布事件队列. */
        final List<Object> eventQueue = new ArrayList<>();

        /** 当前线程是否处于发送事件的过程中. */
        boolean isPosting;

        /** 当前线程是否是主线程. */
        boolean isMainThread;

        /** 处理当前分发的订阅事件的订阅者. */
        Subscription subscription;

        /** 当前准备分发的订阅事件. */
        Object event;

        /** 当前线程分发是否被取消. */
        boolean canceled;
    }

    /** 事件分发. */
    public void post(Object event) {
        // 获取当前线程的Posting状态.
        PostingThreadState postingState = currentPostingThreadState.get();
        // 获取当前线程的事件队列.
        List<Object> eventQueue = postingState.eventQueue;
        eventQueue.add(event);

        if (!postingState.isPosting) {
            postingState.isMainThread = Looper.getMainLooper() == Looper.myLooper();
            postingState.isPosting = true;
            if (postingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                // 循环处理当前线程eventQueue中的每一个event对象.
                while (!eventQueue.isEmpty()) {
                    postSingleEvent(eventQueue.remove(0), postingState);
                }
            } finally {
                // 处理完知乎重置postingState一些标识信息.
                postingState.isPosting = false;
                postingState.isMainThread = false;
            }
        }
    }

    private void postSingleEvent(Object event, PostingThreadState postingState) {
        Class<?> eventClass = event.getClass();
        boolean subscriptionFound = false;
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h ++) {
                Class<?> clazz = eventTypes.get(h);
                subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
            }
        } else {
            subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
        }

        if (!subscriptionFound) {
            if (logNoSubscriberMessages) {
                Log.d("EventBus", "No subscribers registered for event " + eventClass);
            }
            if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                    eventClass != SubscriberExceptionEvent.class) {
                post(new NoSubscriberEvent(this, event));
            }
        }
    }

    /** 找出当前订阅事件类类型eventClass的所有父类的类类型和其实现的接口的类类型. */
    private static List<Class<?>> lookupAllEventTypes(Class<?> eventClass) {
        synchronized (eventTypesCache) {
            List<Class<?>> eventTypes = eventTypesCache.get(eventClass);
            if (eventTypes == null) {
                eventTypes = new ArrayList<>();
                Class<?> clazz = eventClass;
                while (clazz != null) {
                    eventTypes.add(clazz);
                    addInterfaces(eventTypes, clazz.getInterfaces());
                    clazz = clazz.getSuperclass();
                }
                eventTypesCache.put(eventClass, eventTypes);
            }
            return eventTypes;
        }
    }

    /** 递归获取指定接口的所有父类接口. */
    private static void addInterfaces(List<Class<?>> eventTypes, Class<?>[] interfaces) {
        for (Class<?> interfaceClass : interfaces) {
            if (!eventTypes.contains(interfaceClass)) {
                eventTypes.add(interfaceClass);
                addInterfaces(eventTypes, interfaceClass.getInterfaces());
            }
        }
    }

    private boolean postSingleEventForEventType(Object event, PostingThreadState postingState,
                                                Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            // 获取订阅事件类类型对应的订阅者信息集合.(register函数时构造的集合)
            subscriptions = subscriptionsByEventType.get(eventClass);
        }

        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (Subscription subscription : subscriptions) {
                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted = false;
                try {
                    // 发布订阅事件给订阅函数
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

    /**
     * Invokes the subscriber if the subscriptions is still active.
     */
    void invokeSubscriber(PendingPost pendingPost) {
        Object event = pendingPost.event;
        Subscription subscription = pendingPost.subscription;
        PendingPost.releasePendingPost(pendingPost);
        if (subscription.active) {
            invokeSubscriber(subscription, event);
        }
    }

    /** 通过反射来执行订阅函数. */
    void invokeSubscriber(Subscription subscription, Object event) {
        try {
            subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
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

    /** 取消订阅. */
    public synchronized void unregister(Object subscriber) {
        // 获取该订阅者所有的订阅事件类类型集合.
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            for (Class<?> eventType : subscribedTypes) {
                unsubscribeByEventType(subscriber, eventType);
            }
            // 从typesBySubscriber删除该<订阅者对象,订阅事件类类型集合>
            typesBySubscriber.remove(subscriber);
        } else {
            Log.e("EventBus", "Subscriber to unregister was not registered before: "
                    + subscriber.getClass());
        }
    }

    /** 从订阅事件对应的订阅者集合中删除取消注册的订阅者. */
    private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
        // 获取订阅事件对应的订阅者信息集合.
        List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions != null) {
            int size = subscriptions.size();
            for (int i = 0; i < size; i ++) {
                Subscription subscription = subscriptions.get(i);
                // 从订阅者集合中删除特定的订阅者.
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
