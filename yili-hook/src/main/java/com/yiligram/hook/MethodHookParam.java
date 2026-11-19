package com.yiligram.hook;

import java.lang.reflect.Member;

/**
 * State of one call of a hooked method, passed to every {@link YiliHook.Callback}.
 * Mirrors Xposed's {@code XC_MethodHook.MethodHookParam}, which exteraGram plugins use.
 */
public final class MethodHookParam {
    /** The hooked method or constructor. */
    public final Member method;
    /** The receiver, or {@code null} for static methods. */
    public Object thisObject;
    /** Arguments; callbacks may replace elements before the original method runs. */
    public Object[] args;

    private Object result;
    private Throwable throwable;
    boolean returnEarly;

    MethodHookParam(Member method, Object thisObject, Object[] args) {
        this.method = method;
        this.thisObject = thisObject;
        this.args = args;
    }

    public Object getResult() {
        return result;
    }

    /** Sets the return value. From a before-callback this skips the original method. */
    public void setResult(Object result) {
        this.result = result;
        this.throwable = null;
        this.returnEarly = true;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public boolean hasThrowable() {
        return throwable != null;
    }

    /** Makes the call throw. From a before-callback this skips the original method. */
    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
        this.result = null;
        this.returnEarly = true;
    }

    public Object getResultOrThrowable() throws Throwable {
        if (throwable != null) {
            throw throwable;
        }
        return result;
    }

    /** Calls the original (unhooked) method with other arguments. */
    public Object invokeOriginalMethod(Object thisObject, Object... args) throws Throwable {
        return YiliHook.invokeOriginal(method, thisObject, args);
    }
}
