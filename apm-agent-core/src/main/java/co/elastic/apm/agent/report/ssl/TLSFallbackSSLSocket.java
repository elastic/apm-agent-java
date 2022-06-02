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
package co.elastic.apm.agent.report.ssl;

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class TLSFallbackSSLSocket extends SSLSocket {

    private static final Logger logger = LoggerFactory.getLogger(TLSFallbackSSLSocket.class);

    static final String TLS_v_1_3 = "TLSv1.3";

    private final TLSFallbackSSLSocketFactory socketFactory;

    private SSLSocket socket;

    TLSFallbackSSLSocket(SSLSocket socket, TLSFallbackSSLSocketFactory socketFactory) {
        this.socket = socket;
        this.socketFactory = socketFactory;
    }

    @Override
    public void startHandshake() throws IOException {

        // known JDK bug: JDK-8236039
        // automatically fallback to another protocol when triggered

        Set<String> enabledProtocols = new HashSet<>(Arrays.asList(socket.getEnabledProtocols()));
        boolean hasTLS13 = enabledProtocols.contains(TLS_v_1_3);
        AtomicBoolean skipTLS13 = socketFactory.skipTLS13();
        if (hasTLS13 && skipTLS13.get()) {
            enabledProtocols.remove(TLS_v_1_3);
            socket.setEnabledProtocols(enabledProtocols.toArray(new String[0]));
            hasTLS13 = false;
        }
        try {
            socket.startHandshake();
        } catch (SSLHandshakeException e) {

            if (hasTLS13 && e.getMessage().contains("should not be presented in")) {

                boolean hasBeenWarned = skipTLS13.getAndSet(true);
                if (!hasBeenWarned) {
                    logger.warn("Workaround for JDK Bug JDK-8236039 applied, will connect without TLS v1.3. Update JRE/JDK to fix this, or disable TLS v1.3 on APM Server as a workaround (apm-server.ssl.supported_protocols)");
                }

                InetAddress socketAddress = socket.getInetAddress();
                int socketPort = socket.getPort();

                if (!socket.isClosed()) {
                    socket.close();
                }

                socket = (SSLSocket) socketFactory.getOriginalFactory().createSocket(socketAddress, socketPort);

                enabledProtocols.remove(TLS_v_1_3);
                socket.setEnabledProtocols(enabledProtocols.toArray(new String[0]));

                socket.startHandshake();
            } else {
                logSslInfo();
                throw e;
            }
        }
    }

    // SSLSocket

    @Override
    public String[] getSupportedCipherSuites() {
        return socket.getSupportedCipherSuites();
    }

    @Override
    public String[] getEnabledCipherSuites() {
        return socket.getEnabledCipherSuites();
    }

    @Override
    public void setEnabledCipherSuites(String[] suites) {
        socket.setEnabledCipherSuites(suites);
    }

    @Override
    public String[] getSupportedProtocols() {
        return socket.getSupportedProtocols();
    }

    @Override
    public String[] getEnabledProtocols() {
        return socket.getEnabledProtocols();
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {
        socket.setEnabledProtocols(protocols);
    }

    @Override
    public SSLSession getSession() {
        return socket.getSession();
    }

    @Override
    public SSLSession getHandshakeSession() {
        return socket.getHandshakeSession();
    }

    @Override
    public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
        socket.addHandshakeCompletedListener(listener);
    }

    @Override
    public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
        socket.removeHandshakeCompletedListener(listener);
    }

    @Override
    public void setUseClientMode(boolean mode) {
        socket.setUseClientMode(mode);
    }

    @Override
    public boolean getUseClientMode() {
        return socket.getUseClientMode();
    }

    @Override
    public void setNeedClientAuth(boolean need) {
        socket.setNeedClientAuth(need);
    }

    @Override
    public boolean getNeedClientAuth() {
        return socket.getNeedClientAuth();
    }

    @Override
    public void setWantClientAuth(boolean want) {
        socket.setWantClientAuth(want);
    }

    @Override
    public boolean getWantClientAuth() {
        return socket.getWantClientAuth();
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {
        socket.setEnableSessionCreation(flag);
    }

    @Override
    public boolean getEnableSessionCreation() {
        return socket.getEnableSessionCreation();
    }

    @Override
    public SSLParameters getSSLParameters() {
        return socket.getSSLParameters();
    }

    @Override
    public void setSSLParameters(SSLParameters params) {
        socket.setSSLParameters(params);
    }

    // Socket

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        try {
            socket.connect(endpoint);
        } catch (IOException e) {
            logSslInfo();
            throw e;
        }
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        try {
            socket.connect(endpoint, timeout);
        } catch (IOException e) {
            logSslInfo();
            throw e;
        }
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        try {
            socket.bind(bindpoint);
        } catch (IOException e) {
            logSslInfo();
            throw e;
        }
    }

    private void logSslInfo() {
        SSLSession session = socket.getSession();
        if (session != null) {
            try {
                logger.info("APM Server certificates: {}", Arrays.toString(session.getPeerCertificates()));
            } catch (SSLPeerUnverifiedException e) {
                logger.info("APM Server identity could not be verified");
            }
            logger.info("Local certificates: {}", Arrays.toString(session.getLocalCertificates()));
        }
    }

    @Override
    public InetAddress getInetAddress() {
        return socket.getInetAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
        return socket.getLocalAddress();
    }

    @Override
    public int getPort() {
        return socket.getPort();
    }

    @Override
    public int getLocalPort() {
        return socket.getLocalPort();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return socket.getRemoteSocketAddress();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return socket.getLocalSocketAddress();
    }

    @Override
    public SocketChannel getChannel() {
        return socket.getChannel();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return socket.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return socket.getOutputStream();
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
        socket.setTcpNoDelay(on);
    }

    @Override
    public boolean getTcpNoDelay() throws SocketException {
        return socket.getTcpNoDelay();
    }

    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
        socket.setSoLinger(on, linger);
    }

    @Override
    public int getSoLinger() throws SocketException {
        return socket.getSoLinger();
    }

    @Override
    public void sendUrgentData(int data) throws IOException {
        socket.sendUrgentData(data);
    }

    @Override
    public void setOOBInline(boolean on) throws SocketException {
        socket.setOOBInline(on);
    }

    @Override
    public boolean getOOBInline() throws SocketException {
        return socket.getOOBInline();
    }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
        socket.setSoTimeout(timeout);
    }

    @Override
    public int getSoTimeout() throws SocketException {
        return socket.getSoTimeout();
    }

    @Override
    public void setSendBufferSize(int size) throws SocketException {
        socket.setSendBufferSize(size);
    }

    @Override
    public int getSendBufferSize() throws SocketException {
        return socket.getSendBufferSize();
    }

    @Override
    public void setReceiveBufferSize(int size) throws SocketException {
        socket.setReceiveBufferSize(size);
    }

    @Override
    public int getReceiveBufferSize() throws SocketException {
        return socket.getReceiveBufferSize();
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
        socket.setKeepAlive(on);
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
        return socket.getKeepAlive();
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        socket.setTrafficClass(tc);
    }

    @Override
    public int getTrafficClass() throws SocketException {
        return socket.getTrafficClass();
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
        socket.setReuseAddress(on);
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        return socket.getReuseAddress();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    @Override
    public void shutdownInput() throws IOException {
        socket.shutdownInput();
    }

    @Override
    public void shutdownOutput() throws IOException {
        socket.shutdownOutput();
    }

    @Override
    public String toString() {
        return socket.toString();
    }

    @Override
    public boolean isConnected() {
        return socket.isConnected();
    }

    @Override
    public boolean isBound() {
        return socket.isBound();
    }

    @Override
    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public boolean isInputShutdown() {
        return socket.isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
        return socket.isOutputShutdown();
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        socket.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

}
