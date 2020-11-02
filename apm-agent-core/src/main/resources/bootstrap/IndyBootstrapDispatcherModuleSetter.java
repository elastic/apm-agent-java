package co.elastic.apm.agent.bci;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class IndyBootstrapDispatcherModuleSetter {

    public static void setJavaBaseModule(Class<?> indyBootstrapDispatcherClass) throws Exception {
        Field moduleField = Class.class.getDeclaredField("module");
        if (moduleField.getType() == Class.forName("java.lang.Module")) {
            Unsafe unsafe = Unsafe.getUnsafe();
            long moduleFieldOffset = unsafe.objectFieldOffset(moduleField);
            Object javaBaseModule = unsafe.getObject(Class.class, moduleFieldOffset);
            unsafe.putObject(indyBootstrapDispatcherClass, moduleFieldOffset, javaBaseModule);
        } else {
            throw new IllegalStateException("Unexpected module field type: " + moduleField.getType().getName());
        }
    }
}
