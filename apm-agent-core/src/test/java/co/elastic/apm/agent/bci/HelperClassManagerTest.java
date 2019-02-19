/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class HelperClassManagerTest {
    static {
        try {
            HelperClassManager.injectClass(HelperClassManagerTest.class.getClassLoader().getParent(), null,
                "co.elastic.apm.agent.bci.HelperClassManagerTest$InnerTestClass$HelperClassInterface", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void testLoadHelperSingleClassLoader() {
        new InnerTestClass().testLoadHelperSingleClassLoader();
    }

    @Test
    void testLoadHelperAnyClassLoader() throws ClassNotFoundException, InterruptedException {
        new InnerTestClass().testLoadHelperAnyClassLoader();
    }

    @Test
    void testFailOnlyOnceSingleClassLoader() {
        final HelperClassManager<Object> helperClassManager = HelperClassManager.ForSingleClassLoader.of(mock(ElasticApmTracer.class),
            "co.elastic.apm.agent.bci.NonExistingHelperClass");
        assertFailLoadingOnlyOnce(helperClassManager, getClass());
    }

    @Test
    void testFailOnlyOnceAnyClassLoader() throws ClassNotFoundException {
        final HelperClassManager<Object> helperClassManager = HelperClassManager.ForAnyClassLoader.of(mock(ElasticApmTracer.class),
            "co.elastic.apm.agent.bci.NonExistingHelperClass");
        URL[] urls = {getClass().getProtectionDomain().getCodeSource().getLocation()};
        ClassLoader targetClassLoader1 = new URLClassLoader(urls, getClass().getClassLoader().getParent());
        Class libClass1 = targetClassLoader1.loadClass("co.elastic.apm.agent.bci.HelperClassManagerTest$InnerTestClass$LibClass");
        assertFailLoadingOnlyOnce(helperClassManager, libClass1);

        ClassLoader targetClassLoader2 = new URLClassLoader(urls, getClass().getClassLoader().getParent());
        Class libClass2 = targetClassLoader2.loadClass("co.elastic.apm.agent.bci.HelperClassManagerTest$InnerTestClass$LibClass");
        assertFailLoadingOnlyOnce(helperClassManager, libClass2);

        // Check that after loading next helper, still remembers failure
        assertThatCode(() -> helperClassManager.doGetForClassLoaderOfClass(libClass1)).doesNotThrowAnyException();
    }

    @SuppressWarnings("UnusedAssignment")
    @Test
    void testCaching() throws ClassNotFoundException, InterruptedException {
        HelperClassManager.ForAnyClassLoader<?> helperClassManager1 = HelperClassManager.ForAnyClassLoader.of(mock(ElasticApmTracer.class),
            "co.elastic.apm.agent.bci.HelperClassManagerTest$InnerTestClass$HelperClassImpl");
        URL[] urls = {getClass().getProtectionDomain().getCodeSource().getLocation()};
        ClassLoader targetClassLoader1 = new URLClassLoader(urls, getClass().getClassLoader().getParent());
        Class libClass1 = targetClassLoader1.loadClass("co.elastic.apm.agent.bci.HelperClassManagerTest$InnerTestClass$LibClass");
        Object helper1 = helperClassManager1.getForClassLoaderOfClass(libClass1);
        assertThat(helperClassManager1.clId2helperMap.containsKey(targetClassLoader1)).isTrue();
        assertThat(helperClassManager1.clId2helperMap.get(targetClassLoader1).get()).isEqualTo(helper1);
        assertThat(HelperClassManager.ForAnyClassLoader.clId2helperImplListMap.size()).isEqualTo(1);
        assertThat(HelperClassManager.ForAnyClassLoader.clId2helperImplListMap.get(targetClassLoader1)).isNotNull();

        HelperClassManager.ForAnyClassLoader<?> helperClassManager2 = HelperClassManager.ForAnyClassLoader.of(mock(ElasticApmTracer.class),
            "co.elastic.apm.agent.bci.HelperClassManagerTest$InnerTestClass$HelperClassImpl");
        Object helper2 = helperClassManager2.getForClassLoaderOfClass(libClass1);
        assertThat(helperClassManager2.clId2helperMap.containsKey(targetClassLoader1)).isTrue();
        assertThat(helperClassManager2.clId2helperMap.get(targetClassLoader1).get()).isEqualTo(helper2);
        assertThat(HelperClassManager.ForAnyClassLoader.clId2helperImplListMap.size()).isEqualTo(1);
        assertThat(HelperClassManager.ForAnyClassLoader.clId2helperImplListMap.get(targetClassLoader1)).isNotNull();

        targetClassLoader1 = null;
        libClass1 = null;
        helper1 = helper2 = null;
        System.gc();
        Thread.sleep(1000);

        // iterators of this map skip stale entries (where referenced key is null)
        assertThat(helperClassManager1.clId2helperMap.approximateSize()).isEqualTo(1);
        assertThat(helperClassManager1.clId2helperMap.iterator().hasNext()).isFalse();
        assertThat(helperClassManager2.clId2helperMap.approximateSize()).isEqualTo(1);
        assertThat(helperClassManager2.clId2helperMap.iterator().hasNext()).isFalse();

        ClassLoader targetClassLoader3 = new URLClassLoader(urls, getClass().getClassLoader().getParent());
        Class libClass3 = targetClassLoader3.loadClass("co.elastic.apm.agent.bci.HelperClassManagerTest$InnerTestClass$LibClass");
        @SuppressWarnings("unused") Object helper3 = helperClassManager1.getForClassLoaderOfClass(libClass3);
        assertThat(helperClassManager1.clId2helperMap.approximateSize()).isEqualTo(1);
        assertThat(helperClassManager1.clId2helperMap.get(targetClassLoader3).get()).isEqualTo(helper3);
        assertThat(HelperClassManager.ForAnyClassLoader.clId2helperImplListMap.size()).isEqualTo(1);
        assertThat(HelperClassManager.ForAnyClassLoader.clId2helperImplListMap.get(targetClassLoader3)).isNotNull();
    }

    private void assertFailLoadingOnlyOnce(HelperClassManager<Object> helperClassManager, Class libClass1) {
        ThrowableAssert.ThrowingCallable throwingCallable = () -> helperClassManager.doGetForClassLoaderOfClass(libClass1);
        assertThatThrownBy(throwingCallable).isInstanceOf(RuntimeException.class);
        assertThat(helperClassManager.getForClassLoaderOfClass(libClass1)).isNull();
        assertThatCode(throwingCallable).doesNotThrowAnyException();
    }

    private static class InnerTestClass {
        private void testLoadHelperSingleClassLoader() {
            final HelperClassManager<HelperClassInterface> helperClassManager = HelperClassManager.ForSingleClassLoader.of(mock(ElasticApmTracer.class),
                "co.elastic.apm.agent.bci.HelperClassManagerTest$InnerTestClass$HelperClassImpl",
                "co.elastic.apm.agent.bci.HelperClassManagerTest$InnerTestClass$AdditionalHelper");

            final HelperClassInterface helper = helperClassManager.getForClassLoaderOfClass(HelperClassManagerTest.class);
            assertThat(helper).isNotNull();
            assertThat(helper.helpMe()).isTrue();
            assertThat(helper.getClass().getClassLoader().getParent()).isEqualTo(HelperClassManagerTest.class.getClassLoader());
        }

        @SuppressWarnings("UnusedAssignment")
        private void testLoadHelperAnyClassLoader() throws ClassNotFoundException, InterruptedException {
            final HelperClassManager<HelperClassInterface> helperClassManager = HelperClassManager.ForAnyClassLoader.of(mock(ElasticApmTracer.class),
                "co.elastic.apm.agent.bci.HelperClassManagerTest$InnerTestClass$HelperClassImpl",
                "co.elastic.apm.agent.bci.HelperClassManagerTest$InnerTestClass$AdditionalHelper");


            URL[] urls = {getClass().getProtectionDomain().getCodeSource().getLocation()};
            ClassLoader targetClassLoader1 = new URLClassLoader(urls, getClass().getClassLoader().getParent());
            Class libClass1 = targetClassLoader1.loadClass("co.elastic.apm.agent.bci.HelperClassManagerTest$InnerTestClass$LibClass");

            HelperClassInterface helper1 = helperClassManager.getForClassLoaderOfClass(libClass1);
            assertThat(helper1).isNotNull();
            assertThat(helper1.helpMe()).isTrue();
            assertThat(helper1.getClass().getClassLoader().getParent()).isEqualTo(targetClassLoader1);

            ClassLoader targetClassLoader2 = new URLClassLoader(urls, getClass().getClassLoader().getParent());
            Class libClass2 = targetClassLoader2.loadClass("co.elastic.apm.agent.bci.HelperClassManagerTest$InnerTestClass$LibClass");
            assertThat(libClass2).isNotEqualTo(libClass1);

            HelperClassInterface helper2 = helperClassManager.getForClassLoaderOfClass(libClass2);
            assertThat(helper2).isNotEqualTo(helper1);
            assertThat(helper2).isNotNull();
            assertThat(helper2.helpMe()).isTrue();
            assertThat(helper2.getClass().getClassLoader().getParent()).isEqualTo(targetClassLoader2);

            WeakReference<HelperClassInterface> helper1ref = new WeakReference<>(helper1);
            WeakReference<ClassLoader> cl1ref = new WeakReference<>(targetClassLoader1);
            WeakReference<Class> libClass1ref = new WeakReference<>(libClass1);
            WeakReference<HelperClassInterface> helper2ref = new WeakReference<>(helper2);
            WeakReference<ClassLoader> cl2ref = new WeakReference<>(targetClassLoader2);
            WeakReference<Class> libClass2ref = new WeakReference<>(libClass2);

            // Keeping only reference to libClass1, thus keeping its class loader alive
            targetClassLoader1 = null;
            helper1 = null;

            // Release all references to lib2, making all of it and its class loader GC-eligible
            targetClassLoader2 = null;
            libClass2 = null;
            helper2 = null;

            System.gc();
            Thread.sleep(1000);

            assertThat(helper1ref.get()).isNotNull();
            assertThat(cl1ref.get()).isNotNull();
            assertThat(libClass1ref.get()).isNotNull();
            assertThat(helper2ref.get()).isNull();
            assertThat(cl2ref.get()).isNull();
            assertThat(libClass2ref.get()).isNull();
        }

        public interface HelperClassInterface {
            boolean helpMe();
        }

        @SuppressWarnings("unused")
        public static class HelperClassImpl implements HelperClassInterface {

            @Override
            public boolean helpMe() {
                return new AdditionalHelper().helpMe2();
            }
        }

        static class AdditionalHelper {

            boolean helpMe2() {
                return true;
            }
        }

        static class LibClass {
        }
    }
}
