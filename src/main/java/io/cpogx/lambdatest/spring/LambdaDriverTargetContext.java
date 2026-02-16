package io.cpogx.lambdatest.spring;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Static bridge so Karate feature code can obtain the Spring-created Target instance.
 */
public final class LambdaDriverTargetContext {

    private static final AtomicReference<LambdaDriverTarget> INSTANCE = new AtomicReference<>();

    private LambdaDriverTargetContext() {
    }

    public static void setInstance(LambdaDriverTarget target) {
        INSTANCE.set(target);
    }

    public static LambdaDriverTarget getInstance() {
        return INSTANCE.get();
    }

    public static void clearInstance() {
        INSTANCE.set(null);
    }
}
