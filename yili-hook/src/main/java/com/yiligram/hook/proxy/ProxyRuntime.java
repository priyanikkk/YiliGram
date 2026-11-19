package com.yiligram.hook.proxy;

import android.util.Log;

import com.yiligram.hook.Coerce;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Entry points called by generated proxy classes; keeps one handler per generated class. */
public final class ProxyRuntime {
    static final String TAG = "YiliProxy";

    /** Returned by a handler to run the parent implementation. */
    public static final Object CALL_SUPER = new Object() {
        @Override
        public String toString() {
            return "ProxyRuntime.CALL_SUPER";
        }
    };

    static final class ClassInfo {
        volatile ProxyHandler handler;
        final Class<?>[] returnTypes;
        final Class<?>[][] ctorParams;

        ClassInfo(Class<?>[] returnTypes, Class<?>[][] ctorParams) {
            this.returnTypes = returnTypes;
            this.ctorParams = ctorParams;
        }
    }

    private static final List<ClassInfo> CLASSES = new ArrayList<>();

    private ProxyRuntime() {
    }

    static synchronized int register(ClassInfo info) {
        CLASSES.add(info);
        return CLASSES.size() - 1;
    }

    private static synchronized ClassInfo info(int classId) {
        return CLASSES.get(classId);
    }

    /** Detaches a generated class from its handler (plugin unloaded): every call runs the parent. */
    public static void release(Class<?> generated) {
        Integer id = ProxyBuilder.classIdOf(generated);
        if (id != null) {
            info(id).handler = null;
        }
    }

    // ---------------------------------------------------------------- called by generated code

    @SuppressWarnings("unused")
    public static Object invoke(int classId, Object self, int method, Object[] args) {
        ClassInfo info = info(classId);
        ProxyHandler h = info.handler;
        if (h == null) {
            return CALL_SUPER;
        }
        Object r;
        try {
            r = h.invoke(self, method, args);
        } catch (Throwable t) {
            Log.e(TAG, "proxy method " + method + " of " + self.getClass().getName() + " failed", t);
            return CALL_SUPER;
        }
        if (r == CALL_SUPER) {
            return r;
        }
        return Coerce.result(info.returnTypes[method], r);
    }

    @SuppressWarnings("unused")
    public static Object[] pre(int classId, int ctor, Object[] args) {
        ClassInfo info = info(classId);
        ProxyHandler h = info.handler;
        if (h == null) {
            return args;
        }
        try {
            Object[] r = h.preConstruct(ctor, args);
            if (r == null) {
                return args;
            }
            if (r.length != args.length) {
                Log.e(TAG, "preconstructor returned " + r.length + " arguments, expected " + args.length);
                return args;
            }
            Class<?>[] types = info.ctorParams[ctor];
            Object[] out = Coerce.args(types, r);
            for (int i = 0; i < types.length; i++) {
                if (out[i] == null && types[i].isPrimitive()) {
                    out[i] = Coerce.defaultValue(types[i]);
                }
            }
            return out;
        } catch (Throwable t) {
            Log.e(TAG, "preconstructor failed", t);
            return args;
        }
    }

    @SuppressWarnings("unused")
    public static void post(int classId, Object self, int ctor, Object[] args) {
        ProxyHandler h = info(classId).handler;
        if (h == null) {
            return;
        }
        try {
            h.postConstruct(self, ctor, args);
        } catch (Throwable t) {
            Log.e(TAG, "constructor hook of " + self.getClass().getName() + " failed", t);
        }
    }

    /** Value a generated method returns when neither Python nor a parent provides one. */
    @SuppressWarnings("unused")
    public static Object defaultResult(int classId, int method) {
        Class<?> t = info(classId).returnTypes[method];
        return t.isPrimitive() && t != void.class ? Coerce.defaultValue(t) : null;
    }

    // ---------------------------------------------------------------- helpers for Python

    /** Instantiates a class, picking the public constructor whose parameters accept the arguments. */
    public static Object newInstance(Class<?> cls, Object[] args) throws Throwable {
        if (args == null) {
            args = new Object[0];
        }
        Constructor<?> best = null;
        Object[] bestArgs = null;
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            if (Modifier.isPrivate(c.getModifiers())) {
                continue;
            }
            Class<?>[] types = c.getParameterTypes();
            if (types.length != args.length) {
                continue;
            }
            Object[] coerced = Coerce.args(types, args);
            if (accepts(types, coerced)) {
                best = c;
                bestArgs = coerced;
                break;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("No constructor of " + cls.getName() + " accepts " + args.length + " such arguments");
        }
        best.setAccessible(true);
        try {
            return best.newInstance(bestArgs);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private static boolean accepts(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            Object a = args[i];
            if (a == null) {
                if (t.isPrimitive()) {
                    return false;
                }
            } else if (!Coerce.boxed(t).isInstance(a)) {
                return false;
            }
        }
        return true;
    }

    public static Object getField(Object obj, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field f = obj.getClass().getField(name);
        return f.get(obj);
    }

    public static void setField(Object obj, String name, Object value) throws ReflectiveOperationException {
        java.lang.reflect.Field f = obj.getClass().getField(name);
        f.set(obj, Coerce.value(f.getType(), value));
    }
}
