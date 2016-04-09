package org.greenrobot.eventbus;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


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
