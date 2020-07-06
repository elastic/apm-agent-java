package co.elastic.apm.agent.bci.classloading;

import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.dynamic.loading.MultipleParentClassLoader;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;

/**
 * The plugin class loader has both the agent class loader and the target class loader as the parent.
 * This is important so that the plugin class loader has direct access to the agent class loader
 * otherwise, filtering class loaders (like OSGi) have a chance to interfere
 *
 * @see co.elastic.apm.agent.bci.IndyBootstrap
 */
public class IndyPluginClassLoader extends ByteArrayClassLoader.ChildFirst {
    public IndyPluginClassLoader(@Nullable ClassLoader targetClassLoader, ClassLoader agentClassLoader, Map<String, byte[]> typeDefinitions) {
        super(new MultipleParentClassLoader(Arrays.asList(agentClassLoader, targetClassLoader)), true, typeDefinitions, PersistenceHandler.MANIFEST);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.equals("java.lang.ThreadLocal")) {
            throw new ClassNotFoundException("The usage of ThreadLocals is not allowed in instrumentation plugins. Use GlobalThreadLocal instead.");
        }
        return super.loadClass(name, resolve);
    }
}
