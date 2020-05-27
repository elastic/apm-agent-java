/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.urlconnection;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.security.Permission;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

public class HttpUrlConnectionInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    @Override
    protected void performGet(String path) throws Exception {
        final HttpURLConnection urlConnection = new HttpURLConnectionWrapper(new HttpURLConnectionWrapper((HttpURLConnection) new URL(path).openConnection()));
        urlConnection.getInputStream();
        urlConnection.disconnect();
    }

    @Test
    public void testEndInDifferentThread() throws Exception {
        final HttpURLConnection urlConnection = new HttpURLConnectionWrapper(new HttpURLConnectionWrapper((HttpURLConnection) new URL(getBaseUrl() + "/").openConnection()));
        urlConnection.connect();
        AbstractSpan<?> active = tracer.getActive();
        final Thread thread = new Thread(() -> {
            try (Scope scope = active.activateInScope()) {
                urlConnection.getInputStream();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
        thread.join();

        verifyHttpSpan("/");
    }

    @Test
    public void testDisconnectionWithoutExecute() throws Exception {
        final HttpURLConnection urlConnection = new HttpURLConnectionWrapper(new HttpURLConnectionWrapper((HttpURLConnection) new URL(getBaseUrl() + "/").openConnection()));
        urlConnection.connect();
        urlConnection.disconnect();
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getSpans().get(0).getContext().getHttp().getStatusCode()).isEqualTo(0);
    }

    @Test
    public void testMultipleConnect() throws Exception {
        final HttpURLConnection urlConnection = new HttpURLConnectionWrapper(new HttpURLConnectionWrapper((HttpURLConnection) new URL(getBaseUrl() + "/").openConnection()));
        urlConnection.connect();
        urlConnection.connect();
        urlConnection.getInputStream();

        verifyHttpSpan("/");
    }

    @Test
    public void testGetOutputStream() throws Exception {
        final HttpURLConnection urlConnection = new HttpURLConnectionWrapper(new HttpURLConnectionWrapper((HttpURLConnection) new URL(getBaseUrl() + "/").openConnection()));
        urlConnection.setDoOutput(true);
        urlConnection.getOutputStream();
        urlConnection.getInputStream();

        verifyHttpSpan("/");
    }

    @Test
    public void testConnectAfterInputStream() throws Exception {
        final HttpURLConnection urlConnection = new HttpURLConnectionWrapper(new HttpURLConnectionWrapper((HttpURLConnection) new URL(getBaseUrl() + "/").openConnection()));
        urlConnection.getInputStream();
        // should not create another span
        // works because the connected flag is checked
        urlConnection.connect();
        urlConnection.disconnect();

        verifyHttpSpan("/");
    }

    @Test
    @Ignore
    public void testFakeReuse() throws Exception {
        final HttpURLConnection urlConnection = new HttpURLConnectionWrapper(new HttpURLConnectionWrapper((HttpURLConnection) new URL(getBaseUrl() + "/").openConnection()));
        urlConnection.getInputStream();
        urlConnection.disconnect();

        // reusing HttpURLConnection instances is not supported
        // the following calls will essentially be noops
        // but the agent wrongly creates spans
        // however, we don't consider this to be a big problem
        // as it is unlikely someone uses it that way and because the consequences are not severe
        // there is a span being created, but the activation does not leak
        urlConnection.getInputStream();
        urlConnection.disconnect();

        verify(1, getRequestedFor(urlPathEqualTo("/")));
        verifyHttpSpan("/");
    }

    // The actual HttpURLConnection is loaded by the bootstrap class loader which we can't instrument in tests
    // That's why we wrap the connection into a wrapper which actually gets instrumented.
    public static class HttpURLConnectionWrapper extends HttpURLConnection {

        private static final Field CONNECTED;
        private static final Field RESPONSE_CODE;

        static {
            try {
                CONNECTED = URLConnection.class.getDeclaredField("connected");
                CONNECTED.setAccessible(true);
                RESPONSE_CODE = HttpURLConnection.class.getDeclaredField("responseCode");
                RESPONSE_CODE.setAccessible(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private final HttpURLConnection delegate;

        HttpURLConnectionWrapper(HttpURLConnection delegate) {
            super(delegate.getURL());
            this.delegate = delegate;
        }

        private void updateFields() {
            connected = getConnectedFromDelegate();
            responseCode = getResponseCodeFromDelegate();
        }

        private boolean getConnectedFromDelegate() {
            try {
                return (Boolean) CONNECTED.get(delegate);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private int getResponseCodeFromDelegate() {
            try {
                return (Integer) RESPONSE_CODE.get(delegate);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void connect() throws IOException {
            try {
                delegate.connect();
            } finally {
                updateFields();
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            try {
                return delegate.getInputStream();
            } finally {
                updateFields();
            }
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            try {
                return delegate.getOutputStream();
            } finally {
                updateFields();
            }
        }

        @Override
        public void disconnect() {
            try {
                delegate.disconnect();
            } finally {
                updateFields();
            }
        }

        @Override
        public int getResponseCode() throws IOException {
            try {
                return delegate.getResponseCode();
            } finally {
                updateFields();
            }
        }

        @Override
        public boolean usingProxy() {
            return delegate.usingProxy();
        }

        @Override
        public int getConnectTimeout() {
            return delegate.getConnectTimeout();
        }

        @Override
        public void setConnectTimeout(int timeout) {
            delegate.setConnectTimeout(timeout);
        }

        @Override
        public int getReadTimeout() {
            return delegate.getReadTimeout();
        }

        @Override
        public void setReadTimeout(int timeout) {
            delegate.setReadTimeout(timeout);
        }

        @Override
        public URL getURL() {
            return delegate.getURL();
        }

        @Override
        public int getContentLength() {
            return delegate.getContentLength();
        }

        @Override
        public long getContentLengthLong() {
            return delegate.getContentLengthLong();
        }

        @Override
        public String getContentType() {
            return delegate.getContentType();
        }

        @Override
        public String getContentEncoding() {
            return delegate.getContentEncoding();
        }

        @Override
        public long getExpiration() {
            return delegate.getExpiration();
        }

        @Override
        public long getDate() {
            return delegate.getDate();
        }

        @Override
        public long getLastModified() {
            return delegate.getLastModified();
        }

        @Override
        public String getHeaderField(String name) {
            return delegate.getHeaderField(name);
        }

        @Override
        public Map<String, List<String>> getHeaderFields() {
            return delegate.getHeaderFields();
        }

        @Override
        public int getHeaderFieldInt(String name, int Default) {
            return delegate.getHeaderFieldInt(name, Default);
        }

        @Override
        public long getHeaderFieldLong(String name, long Default) {
            return delegate.getHeaderFieldLong(name, Default);
        }

        @Override
        public Object getContent() throws IOException {
            return delegate.getContent();
        }

        @Override
        public Object getContent(Class<?>[] classes) throws IOException {
            return delegate.getContent(classes);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        @Override
        public boolean getDoInput() {
            return delegate.getDoInput();
        }

        @Override
        public void setDoInput(boolean doinput) {
            delegate.setDoInput(doinput);
        }

        @Override
        public boolean getDoOutput() {
            return delegate.getDoOutput();
        }

        @Override
        public void setDoOutput(boolean dooutput) {
            delegate.setDoOutput(dooutput);
        }

        @Override
        public boolean getAllowUserInteraction() {
            return delegate.getAllowUserInteraction();
        }

        @Override
        public void setAllowUserInteraction(boolean allowuserinteraction) {
            delegate.setAllowUserInteraction(allowuserinteraction);
        }

        @Override
        public boolean getUseCaches() {
            return delegate.getUseCaches();
        }

        @Override
        public void setUseCaches(boolean usecaches) {
            delegate.setUseCaches(usecaches);
        }

        @Override
        public long getIfModifiedSince() {
            return delegate.getIfModifiedSince();
        }

        @Override
        public void setIfModifiedSince(long ifmodifiedsince) {
            delegate.setIfModifiedSince(ifmodifiedsince);
        }

        @Override
        public boolean getDefaultUseCaches() {
            return delegate.getDefaultUseCaches();
        }

        @Override
        public void setDefaultUseCaches(boolean defaultusecaches) {
            delegate.setDefaultUseCaches(defaultusecaches);
        }

        @Override
        public void setRequestProperty(String key, String value) {
            delegate.setRequestProperty(key, value);
        }

        @Override
        public void addRequestProperty(String key, String value) {
            delegate.addRequestProperty(key, value);
        }

        @Override
        public String getRequestProperty(String key) {
            return delegate.getRequestProperty(key);
        }

        @Override
        public Map<String, List<String>> getRequestProperties() {
            return delegate.getRequestProperties();
        }

        @Override
        public void setAuthenticator(Authenticator auth) {
            delegate.setAuthenticator(auth);
        }

        @Override
        public String getHeaderFieldKey(int n) {
            return delegate.getHeaderFieldKey(n);
        }

        @Override
        public void setFixedLengthStreamingMode(int contentLength) {
            delegate.setFixedLengthStreamingMode(contentLength);
        }

        @Override
        public void setFixedLengthStreamingMode(long contentLength) {
            delegate.setFixedLengthStreamingMode(contentLength);
        }

        @Override
        public void setChunkedStreamingMode(int chunklen) {
            delegate.setChunkedStreamingMode(chunklen);
        }

        @Override
        public String getHeaderField(int n) {
            return delegate.getHeaderField(n);
        }

        @Override
        public boolean getInstanceFollowRedirects() {
            return delegate.getInstanceFollowRedirects();
        }

        @Override
        public void setInstanceFollowRedirects(boolean followRedirects) {
            delegate.setInstanceFollowRedirects(followRedirects);
        }

        @Override
        public String getRequestMethod() {
            return delegate.getRequestMethod();
        }

        @Override
        public void setRequestMethod(String method) throws ProtocolException {
            delegate.setRequestMethod(method);
        }

        @Override
        public String getResponseMessage() throws IOException {
            return delegate.getResponseMessage();
        }

        @Override
        public long getHeaderFieldDate(String name, long Default) {
            return delegate.getHeaderFieldDate(name, Default);
        }

        @Override
        public Permission getPermission() throws IOException {
            return delegate.getPermission();
        }

        @Override
        public InputStream getErrorStream() {
            return delegate.getErrorStream();
        }

    }
}
