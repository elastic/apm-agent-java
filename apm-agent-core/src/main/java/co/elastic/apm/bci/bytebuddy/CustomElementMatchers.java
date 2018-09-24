package co.elastic.apm.bci.bytebuddy;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.none;

public class CustomElementMatchers {

    public static ElementMatcher.Junction<TypeDescription> isInAnyPackage(Collection<String> includedPackages,
                                                                          ElementMatcher.Junction<TypeDescription> defaultIfEmpty) {
        if (includedPackages.isEmpty()) {
            return defaultIfEmpty;
        }
        ElementMatcher.Junction<TypeDescription> matcher = none();
        for (String applicationPackage : includedPackages) {
            matcher = matcher.or(nameStartsWith(applicationPackage));
        }
        return matcher;
    }

    public static ElementMatcher.Junction<ClassLoader> classLoaderCanLoadClass(String className) {
        return new ElementMatcher.Junction.AbstractBase<>() {

            private final boolean loadableByBootstrapClassLoader = canLoadClass(ClassLoader.getSystemClassLoader().getParent(), className);
            private WeakConcurrentMap<ClassLoader, Boolean> cache = new WeakConcurrentMap.WithInlinedExpunction<>();

            @Override
            public boolean matches(@Nullable ClassLoader target) {
                if (target == null) {
                    return loadableByBootstrapClassLoader;
                }

                Boolean result = cache.get(target);
                if (result == null) {
                    result = canLoadClass(target, className);
                    cache.put(target, result);
                }
                return result;
            }
        };
    }

    private static boolean canLoadClass(ClassLoader target, String className) {
        boolean result;
        try {
            target.loadClass(className);
            result = true;
        } catch (ClassNotFoundException ignore) {
            result = false;
        }
        return result;
    }
}
