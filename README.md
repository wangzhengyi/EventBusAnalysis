# EventBus

## 基础概念

EventBus是一个Android事件发布/订阅框架,通过解耦发布者和订阅者简化Android事件传递.事件传递既可以用于Android四大组件间的通讯,也可以用于用户异步线程和主线程间通讯等.
传统的事件传递方法包括:Handler,BroadCastReceiver,interface回调,相比于EventBus,EventBus的代码更加简洁,代码简单,而且事件发布和订阅充分解耦.
基本概念如下:

* 事件(Event): 可以称为消息,其实就是一个对象.事件类型(EventType)指事件所属的Class.
* 订阅者(Subscriber): 订阅某种事件类型的对象.当有发布者发布这类事件后,EventBus会执行订阅者的被Subscribe注解修饰的函数,这个函数叫事件响应函数.订阅者通过register接口订阅某个事件类型,unregister接口退订.订阅者存在优先级,优先级高的订阅者可以取消事件继续向优先级低的订阅者分发,默认所有订阅者优先级都为0.
* 发布者(Publisher): 发布某事件的对象,通过EventBus.getDefault.post方法发布事件.

## 构造EventBus

EventBus的默认构造方法如下:
```java
EventBus.getDefault();
```

源码如下:
```java
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
```

### EventBusBuilder.java

再去了解EventBus具体构造函数之前,需要先看一下EventBusBuilder的具体内容,中文注释源码如下:
```java
/**
 * 构建器模式
 * Effective Java : 遇到多个构造器参数时要考虑用构造器.
 */
@SuppressWarnings("unused")
public class EventBusBuilder {
    private final static ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    /** 是否监听异常日志. */
    boolean logSubscriberExceptions = true;

    /** 如果没有订阅者,显示log信息. */
    boolean logNoSubscriberMessages = true;

    /** 是否发送监听到的异常. */
    boolean sendSubscriberExceptionEvent = true;

    /** 如果没有订阅者,就发布一条默认事件. */
    boolean sendNoSubscriberEvent = true;

    /** 如果失败,则抛出异常. */
    boolean throwSubscriberException;

    /** Event的子类是否也能响应订阅者. */
    boolean eventInheritance = true;
    boolean ignoreGeneratedIndex;

    /** 是否为严格模式.值为true时,当Subscribe注解描述的响应函数不符合要求时,会抛出相应的异常. */
    boolean strictMethodVerification;

    /** 线程池. */
    ExecutorService executorService = DEFAULT_EXECUTOR_SERVICE;

    /** 从命名来看,含义是不遍历的Method响应函数集合,但是没啥软用,EventBus3.0版本也没有遍历这个集合. */
    List<Class<?>> skipMethodVerificationForClasses;
    List<SubscriberInfoIndex> subscriberInfoIndexes;

    EventBusBuilder() {
    }

    /** Default: true. */
    public EventBusBuilder logSubscriberExceptions(boolean logSubscriberExceptions) {
        this.logSubscriberExceptions = logSubscriberExceptions;
        return this;
    }

    /** Default: true. */
    public EventBusBuilder logNoSubscriberMessages(boolean logNoSubscriberMessages) {
        this.logNoSubscriberMessages = logNoSubscriberMessages;
        return this;
    }

    /** Default: true. */
    public EventBusBuilder sendSubscriberExceptionEvent(boolean sendSubscriberExceptionEvent) {
        this.sendSubscriberExceptionEvent = sendSubscriberExceptionEvent;
        return this;
    }

    /** Default: true. */
    public EventBusBuilder sendNoSubsciberEvent(boolean sendNoSubscriberEvent) {
        this.sendNoSubscriberEvent = sendNoSubscriberEvent;
        return this;
    }

    /**
     * Fails if an subscriber throws an exception (default: false).
     */
    public EventBusBuilder throwSubscriberException(boolean throwSubscriberException) {
        this.throwSubscriberException = throwSubscriberException;
        return this;
    }

    /**
     * By default, EventBus considers the event class hierarchy
     * (subscribers to super classes will be notified).
     */
    public EventBusBuilder eventInheritance(boolean eventInheritance) {
        this.eventInheritance = eventInheritance;
        return this;
    }

    /**
     * Provide a custom thread pool to EventBus used for async and background event delivery.
     */
    public EventBusBuilder executorService(ExecutorService executorService) {
        this.executorService = executorService;
        return this;
    }

    public EventBusBuilder skipMethodVerificationFor(Class<?> clazz) {
        if (skipMethodVerificationForClasses == null) {
            skipMethodVerificationForClasses = new ArrayList<>();
        }
        skipMethodVerificationForClasses.add(clazz);
        return this;
    }

    /**
     * Forces the use of reflection even if there's a generated index.(default: false)
     */
    public EventBusBuilder ignoreGeneratedIndex(boolean ignoreGeneratedIndex) {
        this.ignoreGeneratedIndex = ignoreGeneratedIndex;
        return this;
    }

    /** Default: false. */
    public EventBusBuilder strictMethodVerification(boolean strictMethodVerification) {
        this.strictMethodVerification = strictMethodVerification;
        return this;
    }

    /**
     * Adds an index generated by EventBus' annotation preprocessor.
     */
    public EventBusBuilder addIndex(SubscriberInfoIndex index) {
        if (subscriberInfoIndexes == null) {
            subscriberInfoIndexes = new ArrayList<>();
        }
        subscriberInfoIndexes.add(index);
        return this;
    }

    /**
     * Installs the default EventBus returned by {@link EventBus#getDefault()}
     * using this builder's values.
     */
    public EventBus installDefaultEventBus() {
        synchronized (EventBus.class) {
            if (EventBus.defaultInstance != null) {
                throw  new EventBusException("Default instance already exists." +
                        "It may be only set once before it's used the first time to " +
                        "ensure consistent behavior.");
            }
            EventBus.defaultInstance = build();
            return EventBus.defaultInstance;
        }
    }

    /**
     * Builds an EventBus based on the current configuration.
     */
    public EventBus build() {
        return new EventBus(this);
    }
}
```

### EventBus的构造函数

了解了EventBusBuilder的构造器模式之后,我们就可以去看一下EventBus的默认构造函数了.
```java
/** Map<订阅事件, 订阅该事件的订阅者集合> */
private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;

/** Map<订阅者, 订阅事件集合> */
private final Map<Object, List<Class<?>>> typesBySubscriber;

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

/** EventBusBuilder构造器中的同一成员. */
private final boolean throwSubscriberException;
private final boolean logSubscriberExceptions;
private final boolean logNoSubscriberMessages;
private final boolean sendSubscriberExceptionEvent;
private final boolean sendNoSubscriberEvent;
private final boolean eventInheritance;

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
```

了解了EventBus的构造函数,那接下来,我们就要进入EventBus的注册流程和发送事件流程了.

## 注册流程

订阅者向EventBus注册时,自身首先需要完成两件事情:
1. 订阅者本身需要声明只有一个参数的public方法,并且使用Subscribe进行注解.
2. 订阅者需要调用EventBus的register方法进行注册.

示例代码如下：
```java
public class MessageEvent {}

class Subscriber extents Activity{
    /** 声明订阅函数. */
    @Subscibe(threadMode = ThreadMode.MAIN)
    public void showToast(MessageEvent event) {
        Toast.makeText(this, "show toast", Toast.LENGTH_LONG).show();
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /** 注册当前订阅者类. */
        EvnetBus.getDefault().register(this);
    }
}
```

针对上述两个必须完成的事情,我们分别进行讲解.

### Subscribe.java

Subscribe注解是EventBus3.0版本之后添加的,用来标识当前订阅者类中的订阅函数.
之前EventBus的版本是遍历onEvent事件开头的函数来作为订阅函数,有很多局限性(例如函数命名等),使用注解更加规范而且更加灵活一些.
Subscribe注解的中文注释源码如下:
```java
/** 注解的生命周期是:RUNTIME,注解对象是:Method,并且可以被javadoc等工具文档化. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Subscribe {
    /** 标记订阅方法所在线程的模型. */
    ThreadMode threadMode() default ThreadMode.POSTING;

    /** 标记订阅方法是否为sticky类型. */
    boolean sticky() default false;

    /** 标记订阅方法的优先级. */
    int priority() default 0;
}
```

### 注册函数实现


