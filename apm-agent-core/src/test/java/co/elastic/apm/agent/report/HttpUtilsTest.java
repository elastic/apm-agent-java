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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.sdk.DynamicTransformer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.util.CustomEnvVariables;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpUtilsTest extends CustomEnvVariables {

    @Test
    void consumeAndCloseIgnoresNullConnection() {
        HttpUtils.consumeAndClose(null);
    }

    @Test
    void consumeAndCloseNoStreams() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getErrorStream()).thenReturn(null);
        when(connection.getInputStream()).thenReturn(null);

        HttpUtils.consumeAndClose(connection);
    }

    @Test
    void consumeAndCloseException() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);

        InputStream errorStream = mockEmptyInputStream();
        when(connection.getErrorStream()).thenReturn(errorStream);

        when(connection.getInputStream()).thenThrow(IOException.class);

        HttpUtils.consumeAndClose(connection);

        verify(errorStream).close();
    }

    @Test
    void consumeAndCloseResponseContent() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);

        when(connection.getErrorStream()).thenReturn(null);
        InputStream responseStream = mockEmptyInputStream();

        when(connection.getInputStream()).thenReturn(responseStream);

        HttpUtils.consumeAndClose(connection);

        verify(responseStream).close();
    }

    private static InputStream mockEmptyInputStream() throws IOException {
        // very partial mock, but enough for what we want to test
        InputStream stream = mock(InputStream.class);
        when(stream.available()).thenReturn(0);
        when(stream.read()).thenReturn(-1);
        when(stream.read(any())).thenReturn(-1);
        return stream;
    }

    private static final String FACTORY_MSG = "fake handler can't open connections";
    private static final AtomicBoolean factoryActive = new AtomicBoolean(false);

    static {
        // This method to set the global factory is designed to be called once. It is not possible to
        // un-register the factory after registration
        URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {

            @Nullable
            @Override
            public URLStreamHandler createURLStreamHandler(String p) {
                if (!factoryActive.get()) {
                    // The factory will return null when it does not support a given protocol
                    return null;
                }

                // any non-null value returned here will be registered globally
                // In order to reset the default behavior calling URL.setURLStreamHandlerFactory(null) is required.
                return new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL u) throws IOException {
                        throw new IllegalStateException(FACTORY_MSG);
                    }
                };
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "https"})
    void nonDefaultUrlHandler(String protocol) throws IOException {

        // the handler is set in the URL constructor, thus we have to rebuild it
        // every time we expect a different handler.
        String url = protocol + "://not.found:9999";

        DynamicTransformer.ensureInstrumented(URL.class, Collections.singleton(TestUrlInstrumentation.class));

        try {
            // test default behavior
            checkDefaultHandler(new URL(url));

            factoryActive.set(true);

            // required to remove registered handlers
            URL.setURLStreamHandlerFactory(null);

            // overridden default handler does not allow opening a connection
            assertThatThrownBy(() -> new URL(url).openConnection())
                .hasMessage(FACTORY_MSG);

            checkDefaultHandler(HttpUtils.withDefaultHandler(new URL(url)));
        } finally {
            factoryActive.set(false);

            // required to remove registered handlers
            URL.setURLStreamHandlerFactory(null);

            // should not impact non-fake URLs
            checkDefaultHandler(new URL(url));
        }
    }


    private void checkDefaultHandler(URL url) {
        // unknown host exception is expected here
        assertThatThrownBy(() -> url.openConnection().getInputStream())
            .isInstanceOf(UnknownHostException.class);
    }

    /**
     * Instrumentation of {@link URL#setURLStreamHandlerFactory(URLStreamHandlerFactory)} to allow resetting handlers
     * by calling {@code URL.setURLStreamHandlerFactory(null);}.
     */
    public static final class TestUrlInstrumentation extends ElasticApmInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("java.net.URL");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isStatic().and(named("setURLStreamHandlerFactory"));
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singleton("test-url");
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.report.HttpUtilsTest$TestUrlInstrumentation$AdviceClass";
        }

        public static final class AdviceClass {

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false, skipOn = Advice.OnNonDefaultValue.class)
            public static boolean onEnter(@Advice.Argument(0) @Nullable URLStreamHandlerFactory factory,
                                          @Advice.FieldValue("handlers") Hashtable<?, ?> handlers) {

                if (factory == null) {
                    handlers.clear();
                    return true;
                }
                return false;
            }
        }
    }

}
