package com.yiligram.hook;

import android.os.Build;
import android.util.Log;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Xposed-style Java method hooks for plugins, backed by LSPlant.
 *
 * <p>{@link #hook} replaces the method's entry point once; every later hook of the same method
 * only adds a {@link Callback}. Callbacks run in priority order (higher first) before the
 * original method, and in reverse order after it, like Xposed. A callback that throws is logged
 * and skipped; it never breaks the hooked method.
 *
 * <p>Native hooks are never removed: {@link Unhook#unhook()} only drops the callback, so a call
 * that is running on another thread at that moment stays safe.
 */
public final class YiliHook {
    static final String TAG = "YiliHook";

    /** Implemented by plugins (Python objects through Chaquopy's dynamic_proxy). */
    public interface Callback {
        void before(MethodHookParam param) throws Throwable;

        void after(MethodHookParam param) throws Throwable;
    }

    /** Returned by {@link #hook}; removes that one callback. */
    public static final class Unhook {
        private final HookedMethod hooked;
        private final Callback callback;

        Unhook(HookedMethod hooked, Callback callback) {
            this.hooked = hooked;
            this.callback = callback;
        }

        public Member getHookedMethod() {
            return hooked.target;
        }

        public Callback getCallback() {
            return callback;
        }

        public void unhook() {
            hooked.remove(callback);
        }
    }

    private static final Object LOCK = new Object();
    private static final Map<Member, HookedMethod> HOOKED = new HashMap<>();
    private static int state; // 0 = not tried, 1 = ready, -1 = unavailable
    private static String error;

    private YiliHook() {
    }

    /** Loads the native library and initializes LSPlant once. */
    public static boolean init() {
        synchronized (LOCK) {
            if (state == 0) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // LSPlant calls hidden ART/libcore methods (DexFile, Executable.artMethod).
                        // Only done once a plugin actually hooks something.
                        HiddenApiBypass.addHiddenApiExemptions("L");
                    }
                    System.loadLibrary("yilihook");
                    state = nativeInit() ? 1 : -1;
                    if (state < 0) {
                        error = "LSPlant failed to initialize on this Android version";
                    }
                } catch (Throwable t) {
                    state = -1;
                    error = t.toString();
                    Log.e(TAG, "init failed", t);
                }
            }
            return state > 0;
        }
    }

    public static boolean isAvailable() {
        return init();
    }

    /** Why hooks are unavailable, or null. */
    public static String getError() {
        synchronized (LOCK) {
            return error;
        }
    }

    /**
     * Hooks a method or constructor.
     *
     * @throws IllegalArgumentException for abstract methods and non-executables
     * @throws UnsupportedOperationException when hooking is not available on this device
     * @throws IllegalStateException when LSPlant refuses the method
     */
    public static Unhook hook(Member target, Callback callback, int priority) {
        if (!(target instanceof Method) && !(target instanceof Constructor)) {
            throw new IllegalArgumentException("Only methods and constructors can be hooked: " + target);
        }
        if (Modifier.isAbstract(target.getModifiers())) {
            throw new IllegalArgumentException("Cannot hook an abstract method: " + target);
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback is null");
        }
        if (!init()) {
            throw new UnsupportedOperationException("Java method hooks are unavailable: " + getError());
        }
        HookedMethod hooked;
        synchronized (LOCK) {
            hooked = HOOKED.get(target);
            if (hooked == null) {
                hooked = new HookedMethod(target);
                Method backup = nativeHook(target, hooked, HookedMethod.CALLBACK);
                if (backup == null) {
                    throw new IllegalStateException("LSPlant could not hook " + target);
                }
                backup.setAccessible(true);
                hooked.backup = backup;
                HOOKED.put(target, hooked);
            }
        }
        hooked.add(callback, priority);
        return new Unhook(hooked, callback);
    }

    public static boolean isHooked(Member target) {
        synchronized (LOCK) {
            return HOOKED.containsKey(target);
        }
    }

    /**
     * Makes ART run a method without inlining (or interpreted), so hooks on methods it calls
     * are not bypassed by inlined copies.
     */
    public static boolean deoptimize(Member method) {
        return init() && nativeDeoptimize(method);
    }

    /** Calls the original implementation of a hooked method, or the method itself if not hooked. */
    public static Object invokeOriginal(Member method, Object thisObject, Object[] args) throws Throwable {
        HookedMethod hooked;
        synchronized (LOCK) {
            hooked = HOOKED.get(method);
        }
        if (args == null) {
            args = new Object[0];
        }
        try {
            if (hooked != null) {
                return hooked.backup.invoke(thisObject, Coerce.args(hooked.parameterTypes, args));
            }
            if (method instanceof Method m) {
                m.setAccessible(true);
                return m.invoke(thisObject, Coerce.args(m.getParameterTypes(), args));
            }
            throw new IllegalArgumentException("Cannot invoke a constructor that is not hooked: " + method);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    /** One hooked executable: its backup and the callbacks, sorted by priority. */
    public static final class HookedMethod {
        static final Method CALLBACK;

        static {
            try {
                CALLBACK = HookedMethod.class.getDeclaredMethod("callback", Object[].class);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
        }

        final Member target;
        final boolean isStatic;
        final Class<?>[] parameterTypes;
        final Class<?> returnType;
        volatile Method backup;
        private volatile Entry[] entries = new Entry[0];

        HookedMethod(Member target) {
            this.target = target;
            this.isStatic = Modifier.isStatic(target.getModifiers());
            if (target instanceof Method m) {
                parameterTypes = m.getParameterTypes();
                returnType = m.getReturnType();
            } else {
                parameterTypes = ((Constructor<?>) target).getParameterTypes();
                returnType = void.class;
            }
        }

        synchronized void add(Callback callback, int priority) {
            Entry[] next = Arrays.copyOf(entries, entries.length + 1);
            next[next.length - 1] = new Entry(callback, priority, next.length);
            // Higher priority first; equal priorities keep insertion order.
            Arrays.sort(next, (a, b) -> a.priority != b.priority
                    ? Integer.compare(b.priority, a.priority) : Integer.compare(a.order, b.order));
            entries = next;
        }

        synchronized void remove(Callback callback) {
            Entry[] cur = entries;
            int n = 0;
            Entry[] next = new Entry[cur.length];
            for (Entry e : cur) {
                if (e.callback != callback) {
                    next[n++] = e;
                }
            }
            entries = Arrays.copyOf(next, n);
        }

        /** Called by LSPlant instead of the target. For instance methods all[0] is the receiver. */
        @SuppressWarnings("unused")
        public Object callback(Object[] all) throws Throwable {
            Object thisObject = isStatic ? null : all[0];
            Object[] args = isStatic ? all : Arrays.copyOfRange(all, 1, all.length);
            Entry[] snapshot = entries;
            if (snapshot.length == 0) {
                return invokeBackup(thisObject, args);
            }
            MethodHookParam param = new MethodHookParam(target, thisObject, args);
            int ran = 0;
            for (; ran < snapshot.length; ran++) {
                try {
                    snapshot[ran].callback.before(param);
                } catch (Throwable t) {
                    Log.e(TAG, "before-callback of " + target + " threw", t);
                    param.setResult(null);
                    param.returnEarly = false;
                    continue;
                }
                if (param.returnEarly) {
                    ran++;
                    break;
                }
            }
            if (!param.returnEarly) {
                try {
                    param.setResult(invokeBackup(param.thisObject, param.args));
                } catch (Throwable t) {
                    param.setThrowable(t);
                }
            }
            for (int i = ran - 1; i >= 0; i--) {
                Object lastResult = param.getResult();
                Throwable lastThrowable = param.getThrowable();
                try {
                    snapshot[i].callback.after(param);
                } catch (Throwable t) {
                    Log.e(TAG, "after-callback of " + target + " threw", t);
                    if (lastThrowable == null) {
                        param.setResult(lastResult);
                    } else {
                        param.setThrowable(lastThrowable);
                    }
                }
            }
            if (param.hasThrowable()) {
                throw param.getThrowable();
            }
            return Coerce.result(returnType, param.getResult(), target);
        }

        private Object invokeBackup(Object thisObject, Object[] args) throws Throwable {
            try {
                return backup.invoke(thisObject, Coerce.args(parameterTypes, args));
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }

    private static final class Entry {
        final Callback callback;
        final int priority;
        final int order;

        Entry(Callback callback, int priority, int order) {
            this.callback = callback;
            this.priority = priority;
            this.order = order;
        }
    }

    private static native boolean nativeInit();

    private static native Method nativeHook(Member target, Object hooker, Method callback);

    private static native boolean nativeUnhook(Member target);

    private static native boolean nativeIsHooked(Member target);

    private static native boolean nativeDeoptimize(Member target);
}
