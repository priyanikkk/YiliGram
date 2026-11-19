package com.yiligram.hook.proxy;

import android.os.Build;

import com.android.dx.Code;
import com.android.dx.Comparison;
import com.android.dx.DexMaker;
import com.android.dx.FieldId;
import com.android.dx.Label;
import com.android.dx.Local;
import com.android.dx.MethodId;
import com.android.dx.TypeId;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import dalvik.system.DexClassLoader;
import dalvik.system.InMemoryDexClassLoader;

/**
 * Generates a real Java subclass at runtime (DexMaker) whose methods are implemented by a
 * {@link ProxyHandler} — the engine behind exteraGram's {@code extera_utils.classes}.
 *
 * <p>For every method index the generated method boxes its arguments, calls
 * {@link ProxyRuntime#invoke} and unboxes the result; when the handler answers
 * {@link ProxyRuntime#CALL_SUPER} the parent implementation runs (or a default value for
 * abstract and new methods). Each overridden method also gets a public
 * {@code yili_super_<name>} bridge so Python's {@code super()} can reach the parent. Every
 * accessible parent constructor is mirrored, with pre/post hooks around the super call.
 *
 * <p>Generated classes are loaded through one growing class loader chain, so later proxies can
 * refer to earlier ones.
 */
public final class ProxyBuilder {
    public static final String SUPER_PREFIX = "yili_super_";

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Map<Class<?>, Integer> IDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static ClassLoader sharedLoader;

    private static final int DISPATCH = 0;
    private static final int GETTER = 1;
    private static final int SETTER = 2;

    private static final class Plan {
        final int kind;
        final String name;
        final Class<?> ret;
        final Class<?>[] params;
        /** Parent method this overrides; null for new methods. */
        final Method parent;
        final String field;

        Plan(int kind, String name, Class<?> ret, Class<?>[] params, Method parent, String field) {
            this.kind = kind;
            this.name = name;
            this.ret = ret;
            this.params = params;
            this.parent = parent;
            this.field = field;
        }

        /** A parent class implementation exists (interface default methods are not reachable via super). */
        boolean callsSuper() {
            return parent != null && !Modifier.isAbstract(parent.getModifiers())
                    && !parent.getDeclaringClass().isInterface();
        }
    }

    private final Class<?> base;
    private final Class<?>[] interfaces;
    private final String className;
    private final List<Plan> plans = new ArrayList<>();
    private final Map<String, Class<?>> fields = new LinkedHashMap<>();
    private final Map<String, Method> overridable;
    private boolean built;

    /**
     * @param base       class to extend; an interface means {@code Object} implementing it
     * @param interfaces extra interfaces to implement (may be null)
     * @param nameHint   middle part of the generated class name (sanitized)
     */
    public ProxyBuilder(Class<?> base, Class<?>[] interfaces, String nameHint) {
        List<Class<?>> ifaces = new ArrayList<>();
        if (base.isInterface()) {
            ifaces.add(base);
            base = Object.class;
        }
        if (Modifier.isFinal(base.getModifiers())) {
            throw new IllegalArgumentException("Cannot subclass final class " + base.getName());
        }
        if (interfaces != null) {
            for (Class<?> i : interfaces) {
                if (!i.isInterface()) {
                    throw new IllegalArgumentException(i.getName() + " is not an interface");
                }
                if (!ifaces.contains(i)) {
                    ifaces.add(i);
                }
            }
        }
        this.base = base;
        this.interfaces = ifaces.toArray(new Class<?>[0]);
        String hint = nameHint == null || nameHint.isEmpty() ? "Class" : nameHint.replaceAll("[^A-Za-z0-9_]", "_");
        this.className = "Proxy_" + base.getSimpleName().replaceAll("[^A-Za-z0-9_]", "_") + "_" + hint + "_"
                + Integer.toHexString(System.identityHashCode(this)) + "_" + COUNTER.incrementAndGet();
        this.overridable = collectOverridable(base, this.interfaces);
    }

    public Class<?> getBase() {
        return base;
    }

    public String getClassName() {
        return className;
    }

    // ---------------------------------------------------------------- declarations

    /**
     * Overrides the parent or interface method(s) called {@code name}: the one with these
     * parameter types, or every overload when {@code params} is null. Returns the method indices
     * the handler will receive, in the order of {@link #describe(int)}.
     */
    public int[] override(String name, Class<?>[] params) {
        List<Method> found = new ArrayList<>();
        for (Method m : overridable.values()) {
            if (m.getName().equals(name) && (params == null || Arrays.equals(m.getParameterTypes(), params))) {
                found.add(m);
            }
        }
        if (found.isEmpty()) {
            throw new IllegalArgumentException("No overridable method " + name
                    + (params == null ? "" : Arrays.toString(params)) + " in " + base.getName());
        }
        int[] out = new int[found.size()];
        for (int i = 0; i < out.length; i++) {
            Method m = found.get(i);
            out[i] = add(new Plan(DISPATCH, m.getName(), m.getReturnType(), m.getParameterTypes(), m, null));
        }
        return out;
    }

    /** Declares a new method (or overrides one with the same signature). Returns its index. */
    public int addMethod(String name, Class<?> returnType, Class<?>[] params) {
        Method parent = overridable.get(key(name, params));
        if (parent != null && parent.getReturnType() != returnType) {
            throw new IllegalArgumentException(name + " clashes with " + parent);
        }
        return add(new Plan(DISPATCH, name, returnType, params, parent, null));
    }

    public void addField(String name, Class<?> type) {
        if (fields.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate field " + name);
        }
        fields.put(name, type);
    }

    /** Java-only getter returning a generated field. */
    public void addGetter(String methodName, String field) {
        Class<?> t = requireField(field);
        add(new Plan(GETTER, methodName, t, new Class<?>[0], overridable.get(key(methodName, new Class<?>[0])), field));
    }

    /** Java-only setter writing a generated field. */
    public void addSetter(String methodName, String field) {
        Class<?> t = requireField(field);
        Class<?>[] p = {t};
        add(new Plan(SETTER, methodName, void.class, p, overridable.get(key(methodName, p)), field));
    }

    /** "name(type, …) -> type" of a method index, for diagnostics. */
    public String describe(int index) {
        Plan p = plans.get(index);
        StringBuilder sb = new StringBuilder(p.name).append('(');
        for (int i = 0; i < p.params.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(p.params[i].getName());
        }
        return sb.append(") -> ").append(p.ret.getName()).toString();
    }

    public Class<?>[] getParameterTypes(int index) {
        return plans.get(index).params.clone();
    }

    /** Public and protected constructors of the base class, in constructor-index order. */
    public Constructor<?>[] getConstructors() {
        return constructors().toArray(new Constructor<?>[0]);
    }

    private Class<?> requireField(String field) {
        Class<?> t = fields.get(field);
        if (t == null) {
            throw new IllegalArgumentException("Unknown field " + field);
        }
        return t;
    }

    private int add(Plan plan) {
        if (built) {
            throw new IllegalStateException("Class already built");
        }
        String k = key(plan.name, plan.params);
        for (Plan p : plans) {
            if (key(p.name, p.params).equals(k)) {
                throw new IllegalArgumentException("Method declared twice: " + k);
            }
        }
        plans.add(plan);
        return plans.size() - 1;
    }

    // ---------------------------------------------------------------- generation

    /** Generates and loads the class; the handler receives every call. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public synchronized Class<?> build(ProxyHandler handler) throws Exception {
        if (built) {
            throw new IllegalStateException("Class already built");
        }
        // Abstract methods nobody implemented still need a body.
        Set<String> declared = new HashSet<>();
        for (Plan p : plans) {
            declared.add(key(p.name, p.params));
        }
        for (Method m : overridable.values()) {
            if (Modifier.isAbstract(m.getModifiers()) && !declared.contains(key(m.getName(), m.getParameterTypes()))) {
                plans.add(new Plan(DISPATCH, m.getName(), m.getReturnType(), m.getParameterTypes(), m, null));
            }
        }
        built = true;

        List<Constructor<?>> ctors = constructors();
        Class<?>[] returnTypes = new Class<?>[plans.size()];
        for (int i = 0; i < returnTypes.length; i++) {
            returnTypes[i] = plans.get(i).ret;
        }
        Class<?>[][] ctorParams = new Class<?>[ctors.size()][];
        for (int i = 0; i < ctorParams.length; i++) {
            ctorParams[i] = ctors.get(i).getParameterTypes();
        }
        ProxyRuntime.ClassInfo info = new ProxyRuntime.ClassInfo(returnTypes, ctorParams);
        int classId = ProxyRuntime.register(info);

        DexMaker dm = new DexMaker();
        TypeId gen = TypeId.get("L" + className + ";");
        TypeId[] ifaceIds = new TypeId[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            ifaceIds[i] = TypeId.get(interfaces[i]);
        }
        dm.declare(gen, className + ".generated", Modifier.PUBLIC, TypeId.get(base), ifaceIds);
        for (Map.Entry<String, Class<?>> f : fields.entrySet()) {
            dm.declare(gen.getField(TypeId.get(f.getValue()), f.getKey()), Modifier.PUBLIC, null);
        }
        for (int i = 0; i < ctors.size(); i++) {
            generateConstructor(dm, gen, classId, i, ctors.get(i));
        }
        for (int i = 0; i < plans.size(); i++) {
            Plan p = plans.get(i);
            if (p.kind == GETTER) {
                generateGetter(dm, gen, p);
            } else if (p.kind == SETTER) {
                generateSetter(dm, gen, p);
            } else {
                generateDispatch(dm, gen, classId, i, p);
                if (p.callsSuper()) {
                    generateSuperBridge(dm, gen, p);
                }
            }
        }

        Class<?> cls = load(dm.generate()).loadClass(className);
        IDS.put(cls, classId);
        info.handler = handler;
        return cls;
    }

    static Integer classIdOf(Class<?> generated) {
        return IDS.get(generated);
    }

    private static synchronized ClassLoader load(byte[] dex) throws Exception {
        ClassLoader parent = sharedLoader != null ? sharedLoader : ProxyBuilder.class.getClassLoader();
        ClassLoader loader;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            loader = new InMemoryDexClassLoader(ByteBuffer.wrap(dex), parent);
        } else {
            File dir = new File(System.getProperty("java.io.tmpdir"), "yili_proxies");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IllegalStateException("Cannot create " + dir);
            }
            File jar = new File(dir, "proxy_" + COUNTER.incrementAndGet() + ".dex");
            try (FileOutputStream out = new FileOutputStream(jar)) {
                out.write(dex);
            }
            jar.setReadOnly();
            loader = new DexClassLoader(jar.getPath(), dir.getPath(), null, parent);
        }
        sharedLoader = loader;
        return loader;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void generateDispatch(DexMaker dm, TypeId gen, int classId, int index, Plan p) {
        TypeId ret = TypeId.get(p.ret);
        TypeId[] params = typeIds(p.params);
        Code c = dm.declare(gen.getMethod(ret, p.name, params), Modifier.PUBLIC);
        // All locals first.
        Local self = c.getThis(gen);
        Local[] args = new Local[params.length];
        for (int i = 0; i < params.length; i++) {
            args[i] = c.getParameter(i, params[i]);
        }
        Packer packer = new Packer(c, p.params);
        Local cid = c.newLocal(TypeId.INT);
        Local mix = c.newLocal(TypeId.INT);
        Local selfObj = c.newLocal(TypeId.OBJECT);
        Local res = c.newLocal(TypeId.OBJECT);
        Local sup = c.newLocal(TypeId.OBJECT);
        Unpacker out = p.ret == void.class ? null : new Unpacker(c, p.ret);
        Local superRet = p.ret != void.class && p.callsSuper() ? c.newLocal(ret) : null;

        packer.pack(args);
        c.loadConstant(cid, classId);
        c.loadConstant(mix, index);
        c.cast(selfObj, self);
        c.invokeStatic(RUNTIME_INVOKE, res, cid, selfObj, mix, packer.array);
        c.sget(CALL_SUPER, sup);
        Label callSuper = new Label();
        c.compare(Comparison.EQ, callSuper, res, sup);
        if (out == null) {
            c.returnVoid();
        } else {
            c.returnValue(out.unpack(res));
        }
        c.mark(callSuper);
        if (p.callsSuper()) {
            MethodId parent = TypeId.get(base).getMethod(ret, p.name, params);
            c.invokeSuper(parent, superRet, self, args);
            if (superRet == null) {
                c.returnVoid();
            } else {
                c.returnValue(superRet);
            }
        } else if (out == null) {
            c.returnVoid();
        } else {
            c.returnValue(out.zero());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void generateSuperBridge(DexMaker dm, TypeId gen, Plan p) {
        TypeId ret = TypeId.get(p.ret);
        TypeId[] params = typeIds(p.params);
        Code c = dm.declare(gen.getMethod(ret, SUPER_PREFIX + p.name, params), Modifier.PUBLIC);
        Local self = c.getThis(gen);
        Local[] args = new Local[params.length];
        for (int i = 0; i < params.length; i++) {
            args[i] = c.getParameter(i, params[i]);
        }
        Local r = p.ret == void.class ? null : c.newLocal(ret);
        c.invokeSuper(TypeId.get(base).getMethod(ret, p.name, params), r, self, args);
        if (r == null) {
            c.returnVoid();
        } else {
            c.returnValue(r);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void generateGetter(DexMaker dm, TypeId gen, Plan p) {
        TypeId t = TypeId.get(p.ret);
        Code c = dm.declare(gen.getMethod(t, p.name), Modifier.PUBLIC);
        Local self = c.getThis(gen);
        Local v = c.newLocal(t);
        c.iget(gen.getField(t, p.field), v, self);
        c.returnValue(v);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void generateSetter(DexMaker dm, TypeId gen, Plan p) {
        TypeId t = TypeId.get(p.params[0]);
        Code c = dm.declare(gen.getMethod(TypeId.VOID, p.name, t), Modifier.PUBLIC);
        Local self = c.getThis(gen);
        Local v = c.getParameter(0, t);
        c.iput(gen.getField(t, p.field), self, v);
        c.returnVoid();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void generateConstructor(DexMaker dm, TypeId gen, int classId, int index, Constructor<?> ctor) {
        Class<?>[] types = ctor.getParameterTypes();
        TypeId[] params = typeIds(types);
        Code c = dm.declare(gen.getConstructor(params), Modifier.PUBLIC);
        Local self = c.getThis(gen);
        Local[] args = new Local[params.length];
        for (int i = 0; i < params.length; i++) {
            args[i] = c.getParameter(i, params[i]);
        }
        Packer packer = new Packer(c, types);
        Local cid = c.newLocal(TypeId.INT);
        Local kix = c.newLocal(TypeId.INT);
        Local idx = c.newLocal(TypeId.INT);
        Local newArgs = c.newLocal(TypeId.get(Object[].class));
        Local tmp = c.newLocal(TypeId.OBJECT);
        Local selfObj = c.newLocal(TypeId.OBJECT);
        Unpacker[] unpackers = new Unpacker[types.length];
        for (int i = 0; i < types.length; i++) {
            unpackers[i] = new Unpacker(c, types[i]);
        }

        packer.pack(args);
        c.loadConstant(cid, classId);
        c.loadConstant(kix, index);
        c.invokeStatic(RUNTIME_PRE, newArgs, cid, kix, packer.array);
        Local[] finalArgs = new Local[types.length];
        for (int i = 0; i < types.length; i++) {
            c.loadConstant(idx, i);
            c.aget(tmp, newArgs, idx);
            finalArgs[i] = unpackers[i].unpack(tmp);
        }
        c.invokeDirect(TypeId.get(base).getConstructor(params), null, self, finalArgs);
        c.cast(selfObj, self);
        c.invokeStatic(RUNTIME_POST, null, cid, selfObj, kix, newArgs);
        c.returnVoid();
    }

    // ---------------------------------------------------------------- boxing helpers

    /** Boxes method arguments into a new Object[] (locals are allocated in the constructor). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class Packer {
        final Code c;
        final Class<?>[] types;
        final Local array;
        final Local size;
        final Local idx;
        final Local[] boxes;

        Packer(Code c, Class<?>[] types) {
            this.c = c;
            this.types = types;
            array = c.newLocal(TypeId.get(Object[].class));
            size = c.newLocal(TypeId.INT);
            idx = c.newLocal(TypeId.INT);
            boxes = new Local[types.length];
            for (int i = 0; i < types.length; i++) {
                if (types[i].isPrimitive()) {
                    boxes[i] = c.newLocal(TypeId.get(com.yiligram.hook.Coerce.boxed(types[i])));
                }
            }
        }

        void pack(Local[] args) {
            c.loadConstant(size, types.length);
            c.newArray(array, size);
            for (int i = 0; i < types.length; i++) {
                c.loadConstant(idx, i);
                Local v = args[i];
                if (types[i].isPrimitive()) {
                    Class<?> box = com.yiligram.hook.Coerce.boxed(types[i]);
                    TypeId boxId = TypeId.get(box);
                    c.invokeStatic(boxId.getMethod(boxId, "valueOf", TypeId.get(types[i])), boxes[i], v);
                    v = boxes[i];
                }
                c.aput(array, idx, v);
            }
        }
    }

    /** Converts an Object local to a value of one Java type (locals allocated up front). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class Unpacker {
        final Code c;
        final Class<?> type;
        final Local typed;
        final Local prim;

        Unpacker(Code c, Class<?> type) {
            this.c = c;
            this.type = type;
            typed = c.newLocal(TypeId.get(com.yiligram.hook.Coerce.boxed(type)));
            prim = type.isPrimitive() ? c.newLocal(TypeId.get(type)) : null;
        }

        Local unpack(Local obj) {
            c.cast(typed, obj);
            if (prim == null) {
                return typed;
            }
            Class<?> box = com.yiligram.hook.Coerce.boxed(type);
            TypeId boxId = TypeId.get(box);
            c.invokeVirtual(boxId.getMethod(TypeId.get(type), type.getName() + "Value"), prim, typed);
            return prim;
        }

        Local zero() {
            if (prim == null) {
                c.loadConstant(typed, null);
                return typed;
            }
            Object z;
            if (type == boolean.class) z = Boolean.FALSE;
            else if (type == char.class) z = (char) 0;
            else if (type == long.class) z = 0L;
            else if (type == float.class) z = 0f;
            else if (type == double.class) z = 0d;
            else if (type == short.class) z = (short) 0;
            else if (type == byte.class) z = (byte) 0;
            else z = 0;
            c.loadConstant(prim, z);
            return prim;
        }
    }

    // ---------------------------------------------------------------- reflection helpers

    private static final TypeId<ProxyRuntime> RUNTIME = TypeId.get(ProxyRuntime.class);
    private static final TypeId<Object[]> OBJECT_ARRAY = TypeId.get(Object[].class);
    private static final MethodId<ProxyRuntime, Object> RUNTIME_INVOKE =
            RUNTIME.getMethod(TypeId.OBJECT, "invoke", TypeId.INT, TypeId.OBJECT, TypeId.INT, OBJECT_ARRAY);
    private static final MethodId<ProxyRuntime, Object[]> RUNTIME_PRE =
            RUNTIME.getMethod(OBJECT_ARRAY, "pre", TypeId.INT, TypeId.INT, OBJECT_ARRAY);
    private static final MethodId<ProxyRuntime, Void> RUNTIME_POST =
            RUNTIME.getMethod(TypeId.VOID, "post", TypeId.INT, TypeId.OBJECT, TypeId.INT, OBJECT_ARRAY);
    private static final FieldId<ProxyRuntime, Object> CALL_SUPER = RUNTIME.getField(TypeId.OBJECT, "CALL_SUPER");

    @SuppressWarnings("rawtypes")
    private static TypeId[] typeIds(Class<?>[] classes) {
        TypeId[] out = new TypeId[classes.length];
        for (int i = 0; i < classes.length; i++) {
            out[i] = TypeId.get(classes[i]);
        }
        return out;
    }

    private static String key(String name, Class<?>[] params) {
        StringBuilder sb = new StringBuilder(name).append('(');
        for (Class<?> p : params) {
            sb.append(p.getName()).append(',');
        }
        return sb.append(')').toString();
    }

    private List<Constructor<?>> constructors() {
        List<Constructor<?>> out = new ArrayList<>();
        for (Constructor<?> c : base.getDeclaredConstructors()) {
            int m = c.getModifiers();
            if ((Modifier.isPublic(m) || Modifier.isProtected(m)) && !c.isSynthetic()) {
                out.add(c);
            }
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException(base.getName() + " has no public or protected constructor");
        }
        return out;
    }

    /** Methods a subclass in another package may override, most-derived first, by signature. */
    private static Map<String, Method> collectOverridable(Class<?> base, Class<?>[] interfaces) {
        Map<String, Method> out = new LinkedHashMap<>();
        Set<String> sealed = new HashSet<>();
        for (Class<?> c = base; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                int mod = m.getModifiers();
                String k = key(m.getName(), m.getParameterTypes());
                if (m.isSynthetic() || m.isBridge() || Modifier.isStatic(mod) || Modifier.isPrivate(mod)) {
                    continue;
                }
                if (Modifier.isFinal(mod) || !(Modifier.isPublic(mod) || Modifier.isProtected(mod))) {
                    sealed.add(k);
                    continue;
                }
                if (!sealed.contains(k) && !out.containsKey(k)) {
                    out.put(k, m);
                }
            }
        }
        List<Class<?>> queue = new ArrayList<>(Arrays.asList(interfaces));
        for (Class<?> c = base; c != null; c = c.getSuperclass()) {
            queue.addAll(Arrays.asList(c.getInterfaces()));
        }
        for (Class<?> i : queue) {
            for (Method m : i.getMethods()) {
                if (Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                String k = key(m.getName(), m.getParameterTypes());
                if (!sealed.contains(k) && !out.containsKey(k)) {
                    out.put(k, m);
                }
            }
        }
        return out;
    }
}
