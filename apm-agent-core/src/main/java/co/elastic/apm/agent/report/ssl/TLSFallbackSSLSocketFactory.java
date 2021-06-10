/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.report.ssl;

import javax.annotation.Nullable;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

class TLSFallbackSSLSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory factory;

    private final AtomicBoolean skipTLS13;

    private TLSFallbackSSLSocketFactory(SSLSocketFactory factory) {
        this.factory = factory;
        this.skipTLS13 = new AtomicBoolean(false);
    }

    /**
     * Wraps an existing SSL socket factory to add TLS fallback
     *
     * @param factory SSL factory to wrap
     * @return SSL factory with TLS fallback
     */
    @Nullable
    static TLSFallbackSSLSocketFactory wrapFactory(@Nullable SSLSocketFactory factory) {
        if (factory == null) {
            return null;
        }
        return new TLSFallbackSSLSocketFactory(factory);
    }

    AtomicBoolean skipTLS13() {
        return skipTLS13;
    }

    SSLSocketFactory getOriginalFactory(){
        return factory;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return factory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return factory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return wrapSocket(factory.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket() throws IOException {
        return wrapSocket(factory.createSocket());
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return wrapSocket(factory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        return wrapSocket(factory.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return wrapSocket(factory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return wrapSocket(factory.createSocket(address, port, localAddress, localPort));
    }

    private Socket wrapSocket(Socket socket) {
        return new TLSFallbackSSLSocket((SSLSocket) socket, this);
    }

}
