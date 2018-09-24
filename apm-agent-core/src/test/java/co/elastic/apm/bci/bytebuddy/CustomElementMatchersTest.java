package co.elastic.apm.bci.bytebuddy;

import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.elastic.apm.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static org.assertj.core.api.Assertions.assertThat;

class CustomElementMatchersTest {


    @Test
    void testIncludedPackages() {
        final TypeDescription thisClass = TypeDescription.ForLoadedType.of(getClass());
        assertThat(isInAnyPackage(List.of(), none()).matches(thisClass)).isFalse();
        assertThat(isInAnyPackage(List.of(thisClass.getPackage().getName()), none()).matches(thisClass)).isTrue();
        assertThat(isInAnyPackage(List.of(thisClass.getPackage().getName()), none()).matches(TypeDescription.ForLoadedType.of(Object.class))).isFalse();
    }

    @Test
    void testClassLoaderCanLoadClass() {
        assertThat(classLoaderCanLoadClass(Object.class.getName()).matches(ClassLoader.getSystemClassLoader())).isTrue();
        assertThat(classLoaderCanLoadClass("not.Here").matches(ClassLoader.getSystemClassLoader())).isFalse();
    }
}
