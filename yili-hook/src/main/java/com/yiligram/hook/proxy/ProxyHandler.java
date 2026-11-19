package com.yiligram.hook.proxy;

/**
 * Implemented by the plugin runtime (Python) for one generated class.
 * Generated code calls it through {@link ProxyRuntime}.
 */
public interface ProxyHandler {
    /** Before the parent constructor: replacement arguments, or null to keep them. */
    Object[] preConstruct(int constructor, Object[] args);

    /** After the parent constructor returned. */
    void postConstruct(Object self, int constructor, Object[] args);

    /**
     * A generated or overridden method was called. Return {@link ProxyRuntime#CALL_SUPER} to run
     * the parent implementation (or the default value for methods without one).
     */
    Object invoke(Object self, int method, Object[] args);
}
