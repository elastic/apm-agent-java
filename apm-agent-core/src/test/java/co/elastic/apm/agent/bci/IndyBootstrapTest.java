/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import net.bytebuddy.dynamic.loading.ClassInjector;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class IndyBootstrapTest extends AbstractInstrumentationTest {

    @Test
    void testDispatcherInitialization() throws Exception {
        Logger logger = LoggerFactory.getLogger(IndyBootstrapTest.class);
        Method bootstrap = IndyBootstrap.getIndyBootstrapMethod(logger);
        Method exceptionHandler = IndyBootstrap.getExceptionHandlerMethod(logger);
        Class<?> dispatcher = bootstrap.getDeclaringClass();

        assertThat(dispatcher.getName()).isEqualTo("java.lang.IndyBootstrapDispatcher");
        assertThat(dispatcher.getClassLoader()).isNull();
        assertThat(dispatcher.getModule()).isSameAs(Object.class.getModule());
        assertThat(exceptionHandler.getDeclaringClass()).isSameAs(dispatcher);

        // Exercise initialization again as well as the cached entry points.
        Method init = IndyBootstrap.class.getDeclaredMethod("initIndyBootstrap", Logger.class);
        init.setAccessible(true);
        for (int i = 0; i < 3; i++) {
            assertThat(init.invoke(null, logger)).isSameAs(dispatcher);
            assertThat(IndyBootstrap.getIndyBootstrapMethod(logger)).isSameAs(bootstrap);
            assertThat(IndyBootstrap.getExceptionHandlerMethod(logger)).isSameAs(exceptionHandler);
            assertThat(dispatcher.getField("bootstrap").get(null)).isEqualTo(IndyBootstrap.class.getMethod("bootstrap",
                MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class));
            assertThat(dispatcher.getField("logAdviceException").get(null))
                .isEqualTo(IndyBootstrap.class.getMethod("logExceptionThrownByAdvice", Throwable.class));
        }
    }

    @Test
    void testMissingBootstrapResource() throws Exception {
        String className = "java.lang.MissingIndyBootstrapDispatcher";
        String resourceName = "bootstrap/java/lang/MissingIndyBootstrapDispatcher.esclazz";
        assertThatThrownBy(() -> loadClassInBootstrap(className, resourceName))
            .hasCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("Could not locate " + resourceName);
        assertThatThrownBy(() -> Class.forName(className, false, null)).isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void testModuleOpeningFailure() throws Exception {
        String className = "java.lang.UninitializedIndyBootstrapDispatcher";
        Field instrumentation = ElasticApmAgent.class.getDeclaredField("instrumentation");
        instrumentation.setAccessible(true);
        Object originalInstrumentation = instrumentation.get(null);
        try {
            instrumentation.set(null, null);
            assertThatThrownBy(() -> loadClassInBootstrap(className, "bootstrap/java/lang/IndyBootstrapDispatcher.esclazz"))
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause().hasMessageContaining(className).hasMessageContaining("java.lang")
                .hasRootCauseMessage("Can't open modules before the agent has been initialized");
            assertThatThrownBy(() -> Class.forName(className, false, null)).isInstanceOf(ClassNotFoundException.class);
        } finally {
            instrumentation.set(null, originalInstrumentation);
        }
    }

    private static Object loadClassInBootstrap(String className, String resourceName) throws Exception {
        Method load = IndyBootstrap.class.getDeclaredMethod("loadClassInBootstrap", String.class, String.class, Class.class);
        load.setAccessible(true);
        return load.invoke(null, className, resourceName, Object.class);
    }

    @Test
    void testSetJavaBaseModule() throws Throwable {
        assumeTrue(ClassInjector.UsingUnsafe.isAvailable(), "The legacy module setter requires Unsafe injection");
        Module javaBaseModule = Class.class.getModule();
        assertThat(ModuleSetterTarget.class.getModule()).isNotEqualTo(javaBaseModule);

        IndyBootstrap.setJavaBaseModule(ModuleSetterTarget.class);
        assertThat(ModuleSetterTarget.class.getModule()).isEqualTo(javaBaseModule);
    }

    private static class ModuleSetterTarget {
    }
}
