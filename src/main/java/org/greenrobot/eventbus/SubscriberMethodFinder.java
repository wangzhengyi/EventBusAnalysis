package org.greenrobot.eventbus;

import org.greenrobot.eventbus.meta.SubscriberInfo;
import org.greenrobot.eventbus.meta.SubscriberInfoIndex;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订阅者响应函数发现类.
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"})
class SubscriberMethodFinder {
    /** 这两种是编译器添加的方法,因为需要忽略. */
    private static final int BRIDGE = 0X40;
    private static final int SYNTHETIC = 0X1000;

    /** 定义需要忽略方法的修饰符. */
    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC
            | BRIDGE | SYNTHETIC;

    /** 线程安全的缓存Map,存储的键值对为<订阅者类类型,订阅者方法信息集合>. */
    private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE =
            new ConcurrentHashMap<>();

    /** 这里我们只介绍ignoreGeneratedIndex为true的情况,即只在运行时分析订阅者类的订阅函数信息. */
    private final boolean ignoreGeneratedIndex;

    /** ignoreGeneratedIndex为true时,subscriberInfoIndexes为空集合. */
    private List<SubscriberInfoIndex> subscriberInfoIndexes;
    private final boolean strictMethodVerification;

    /** 对象缓冲池的默认大小. */
    private static final int POOL_SIZE = 4;

    /** FindState对象缓冲池. */
    private static final FindState[] FIND_STATE_POOL = new FindState[POOL_SIZE];

    SubscriberMethodFinder(List<SubscriberInfoIndex> subscriberInfoIndexes,
                           boolean strictMethodVerification, boolean ignoreGeneratedIndex) {
        this.subscriberInfoIndexes = subscriberInfoIndexes;
        this.strictMethodVerification = strictMethodVerification;
        this.ignoreGeneratedIndex = ignoreGeneratedIndex;
    }

    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        // 先从METHOD_CACHE中看是否有缓存.key:订阅者的类类型.value:订阅者的订阅方法信息集合.
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        if (subscriberMethods != null) {
            return subscriberMethods;
        }

        // 通过反射来获取订阅者的订阅方法信息集合.
        subscriberMethods = findUsingReflection(subscriberClass);

        if (subscriberMethods.isEmpty()) {
            throw new EventBusException("Subscriber" + subscriberClass + " and its super classes have no " +
                    "public methods with the @Subscribe annotation");
        } else {
            // 在METHOD_CACHE中缓存订阅者类类型-订阅方法信息集合.
            METHOD_CACHE.put(subscriberClass, subscriberMethods);
            return subscriberMethods;
        }
    }

    private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {
        FindState findState = prepareFindState();
        findState.initForSubscriber(subscriberClass);
        while (findState.clazz != null) {
            // 通过反射获取订阅函数集合.
            findUsingReflectionInSingleClass(findState);
            // 检查订阅者类的父类中是否有符合条件的订阅函数.
            findState.moveToSuperclass();
        }
        return getMethodsAndRelease(findState);
    }

    /** 对象池,复用FindState对象,防止对象被多次new或者gc. */
    private FindState prepareFindState() {
        synchronized (FIND_STATE_POOL) {
            for (int i = 0; i < POOL_SIZE; i ++) {
                FindState state = FIND_STATE_POOL[i];
                if (state != null) {
                    FIND_STATE_POOL[i] = null;
                    return state;
                }
            }
        }

        // 说明对象池中没有被new出的FindState对象,所以需要手动new一个出来,返回给调用者.
        return new FindState();
    }

    /** 通过反射来解析订阅者类获取订阅函数信息. */
    private void findUsingReflectionInSingleClass(FindState findState) {
        Method[] methods;
        try {
            // 获取订阅者所有声明的方法
            methods = findState.clazz.getDeclaredMethods();
        } catch (Throwable th) {
            methods = findState.clazz.getMethods();
            findState.skipSuperClasses = true;
        }

        for (Method method : methods) {
            // 获取当前方法的语言修饰符
            int modifiers = method.getModifiers();
            // 订阅方法必须是public而且不能在被忽略的修饰符集合中[abstract,static,bridge,synthetic].
            if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                // 获取指定方法的形参类型集合
                Class<?>[] parameterTypes = method.getParameterTypes();
                // EventBus从3.0版本之后,订阅函数的标准改为有Subscribe注解修饰
                // 并且只有一个参数,参数类型作为订阅事件的参数类型.
                if (parameterTypes.length == 1) {
                    Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                    if (subscribeAnnotation != null) {
                        Class<?> eventType = parameterTypes[0];
                        if (findState.checkAdd(method, eventType)) {
                            // 封装订阅函数到FindState的subscriberMethods数组中.
                            ThreadMode threadMode = subscribeAnnotation.threadMode();
                            findState.subscriberMethods.add(new SubscriberMethod(
                                    method, eventType, threadMode, subscribeAnnotation.priority(),
                                    subscribeAnnotation.sticky()
                            ));
                        }
                    }
                } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                    // 订阅函数的参数必须只有一个,也就说一个订阅函数只能订阅一个事件.
                    String methodName = method.getDeclaringClass().getName() +
                            "." + method.getName();
                    throw new EventBusException("@Subscribe method" + methodName +
                            " must have exactly 1 parameter but has " + parameterTypes.length);
                }
            } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                // 订阅函数必须是public的,而且不能有[abstract,static,bridge,synthetic]修饰符.
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new EventBusException(methodName + " is a illegal @Subscribe method: " +
                        "must be public, non-static, and non-abstract");
            }
        }
    }

    /** 返回订阅函数List,并释放FindState到对象缓冲池中. */
    private List<SubscriberMethod> getMethodsAndRelease(FindState findState) {
        List<SubscriberMethod> subscriberMethods = new ArrayList<>(findState.subscriberMethods);
        findState.recycle();
        synchronized (FIND_STATE_POOL) {
            for (int i = 0; i < POOL_SIZE; i ++) {
                if (FIND_STATE_POOL[i] == null) {
                    FIND_STATE_POOL[i] = findState;
                    break;
                }
            }
        }
        return subscriberMethods;
    }

    /** 用来对订阅方法进行校验和保存. */
    static class FindState {
        final List<SubscriberMethod> subscriberMethods = new ArrayList<>();
        final Map<Class, Object> anyMethodByEventType = new HashMap<>();
        final Map<String, Class> subscriberClassByMethodKey = new HashMap<>();
        final StringBuilder methodKeyBuilder = new StringBuilder(128);
        Class<?> subscriberClass;
        Class<?> clazz;
        boolean skipSuperClasses;
        SubscriberInfo subscriberInfo;

        void initForSubscriber(Class<?> subscriberClass) {
            this.subscriberClass = clazz = subscriberClass;
            skipSuperClasses = false;
            subscriberInfo = null;
        }

        void recycle() {
            subscriberMethods.clear();
            anyMethodByEventType.clear();
            subscriberClassByMethodKey.clear();
            methodKeyBuilder.setLength(0);
            subscriberClass = null;
            clazz = null;
            skipSuperClasses = false;
            subscriberInfo = null;
        }

        boolean checkAdd(Method method, Class<?> eventType) {
            Object existing = anyMethodByEventType.put(eventType, method);
            if (existing == null) {
                return true;
            } else {
                if (existing instanceof Method) {
                    if (!checkAddWithMethodSignature((Method)existing, eventType)) {
                        throw new IllegalStateException();
                    }
                    anyMethodByEventType.put(eventType, this);
                }
                return checkAddWithMethodSignature(method, eventType);
            }
        }

        private boolean checkAddWithMethodSignature(Method method, Class<?> eventType) {
            methodKeyBuilder.setLength(0);
            methodKeyBuilder.append(method.getName());
            methodKeyBuilder.append('>').append(eventType.getName());
            String methodKey = methodKeyBuilder.toString();
            Class<?> methodClass = method.getDeclaringClass();
            Class<?> methodClassOld = subscriberClassByMethodKey.put(methodKey, methodClass);
            if (methodClassOld == null || methodClassOld.isAssignableFrom(methodClass)) {
                return true;
            } else {
                subscriberClassByMethodKey.put(methodKey, methodClassOld);
                return false;
            }
        }

        /** 获取clazz父类的类类型. */
        void moveToSuperclass() {
            if (skipSuperClasses) {
                clazz = null;
            } else {
                clazz = clazz.getSuperclass();
                String clazzName = clazz.getName();
                if (clazzName.startsWith("java.") || clazzName.startsWith("javax.") ||
                        clazzName.startsWith("android.")) {
                    clazz = null;
                }
            }
        }
    }
}
