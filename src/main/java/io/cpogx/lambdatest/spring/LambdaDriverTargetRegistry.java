package io.cpogx.lambdatest.spring;

public final class LambdaDriverTargetRegistry {

    private static final ThreadLocal<LambdaDriverTarget> HOLDER = new ThreadLocal<>();

    private LambdaDriverTargetRegistry() {
    }

    public static void set(LambdaDriverTarget target) {
        HOLDER.set(target);
    }

    public static LambdaDriverTarget get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
