package com.yiligram.hook;

import android.util.Log;

import java.lang.reflect.Member;

/**
 * Boxes values from scripting languages into what a Java signature expects.
 *
 * <p>Python passes integers as {@code Long} and floats as {@code Double}. A plugin that writes
 * {@code param.args[0] = 5} for an {@code int} parameter, or returns {@code 0} from an
 * {@code int} method, would otherwise crash the call with a ClassCastException.
 */
final class Coerce {
    private Coerce() {
    }

    static Object[] args(Class<?>[] types, Object[] args) {
        if (args.length != types.length) {
            return args; // let reflection report the mismatch
        }
        Object[] out = null;
        for (int i = 0; i < types.length; i++) {
            Object v = value(types[i], args[i]);
            if (v != args[i]) {
                if (out == null) {
                    out = args.clone();
                }
                out[i] = v;
            }
        }
        return out != null ? out : args;
    }

    static Object result(Class<?> type, Object value, Member method) {
        if (type == void.class) {
            return null;
        }
        if (value == null && type.isPrimitive()) {
            // Xposed would throw a NullPointerException in the caller; a default is kinder.
            Log.w(YiliHook.TAG, "null result for primitive " + type + " of " + method + "; using default");
            return defaultValue(type);
        }
        return value(type, value);
    }

    static Object value(Class<?> type, Object v) {
        if (v == null) {
            return null;
        }
        Class<?> box = boxed(type);
        if (box.isInstance(v)) {
            return v;
        }
        if (v instanceof Number n) {
            if (box == Integer.class) return n.intValue();
            if (box == Long.class) return n.longValue();
            if (box == Short.class) return n.shortValue();
            if (box == Byte.class) return n.byteValue();
            if (box == Float.class) return n.floatValue();
            if (box == Double.class) return n.doubleValue();
            if (box == Character.class) return (char) n.intValue();
        }
        if (box == Character.class && v instanceof CharSequence s && s.length() == 1) {
            return s.charAt(0);
        }
        return v;
    }

    static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }

    static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return Boolean.FALSE;
        if (type == char.class) return (char) 0;
        return value(type, 0);
    }
}
