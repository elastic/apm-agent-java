package co.elastic.apm.agent.report.ssl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TLSFallbackSSLSocketTest {

    @Test
    void noHandshakeExceptionNoTLS13() throws IOException {
        SSLSocket sslSocket = mockSocket();
        SSLSocketFactory sslFactory = mock(SSLSocketFactory.class);

        TLSFallbackSSLSocketFactory factory = new TLSFallbackSSLSocketFactory(sslFactory);
        TLSFallbackSSLSocket socket = new TLSFallbackSSLSocket(sslSocket, factory);

        socket.startHandshake();

        verify(sslSocket, never()).setEnabledProtocols(any());
    }

    @Test
    void noHandshakeExceptionWithTLS13() throws IOException {
        SSLSocket sslSocket = mockSocket(TLSFallbackSSLSocket.TLS_v_1_3);
        SSLSocketFactory sslFactory = mock(SSLSocketFactory.class);

        TLSFallbackSSLSocketFactory factory = new TLSFallbackSSLSocketFactory(sslFactory);
        TLSFallbackSSLSocket socket = new TLSFallbackSSLSocket(sslSocket, factory);

        socket.startHandshake();

        verify(sslSocket, never()).setEnabledProtocols(any());
    }

    @Test
    void handshakeExceptionWithoutTLS13() throws IOException {
        handshakeExceptionNoFallback();
    }

    @Test
    void handshakeExceptionWithTLS13() throws IOException {
        handshakeExceptionNoFallback(TLSFallbackSSLSocket.TLS_v_1_3);
    }

    @Test
    void handshakeExceptionWithTLS13ApplyWorkaround() throws IOException {
        SSLHandshakeException exception = new SSLHandshakeException("TEST should not be presented in TEST");

        SSLSocket initialSocket = mockSocket(TLSFallbackSSLSocket.TLS_v_1_3, "p1");
        doThrow(exception).when(initialSocket).startHandshake();

        InetAddress address = InetAddress.getByName("elastic.co");
        int port = 42;
        when(initialSocket.getInetAddress()).thenReturn(address);
        when(initialSocket.getPort()).thenReturn(port);

        // creating fallback socket with same address & port
        SSLSocketFactory sslFactory = mock(SSLSocketFactory.class);
        SSLSocket fallbackSocket = mockSocket(TLSFallbackSSLSocket.TLS_v_1_3, "p1");
        when(sslFactory.createSocket(same(address), same(port)))
            .thenReturn(fallbackSocket);

        TLSFallbackSSLSocketFactory factory = new TLSFallbackSSLSocketFactory(sslFactory);
        TLSFallbackSSLSocket socket = new TLSFallbackSSLSocket(initialSocket, factory);

        socket.startHandshake();

        verify(initialSocket, description("initial socket should have been closed"))
            .close();

        verifyProtocols(fallbackSocket, "p1");

        // once the first workaround has been applied, we should proactively disable TLS 1.3 protocol
        // before handshake

        initialSocket = mockSocket(TLSFallbackSSLSocket.TLS_v_1_3, "p2");
        socket = new TLSFallbackSSLSocket(initialSocket, factory);

        socket.startHandshake();

        verifyProtocols(initialSocket, "p2");
    }

    private void verifyProtocols(SSLSocket socket, String... values) {
        ArgumentCaptor<String[]> fallbackProtocols = ArgumentCaptor.forClass(String[].class);
        verify(socket).setEnabledProtocols(fallbackProtocols.capture());
        assertThat(fallbackProtocols.getValue()).containsOnly(values);
    }


    private static void handshakeExceptionNoFallback(String... protocols) throws IOException {
        SSLHandshakeException exception = new SSLHandshakeException("");

        SSLSocket sslSocket = mockSocket(protocols);
        doThrow(exception).when(sslSocket).startHandshake();

        SSLSocketFactory sslFactory = mock(SSLSocketFactory.class);

        TLSFallbackSSLSocketFactory factory = new TLSFallbackSSLSocketFactory(sslFactory);
        TLSFallbackSSLSocket socket = new TLSFallbackSSLSocket(sslSocket, factory);

        Throwable thrown = null;
        try{
            socket.startHandshake();
        } catch (SSLHandshakeException e){
            thrown = e;
        }
        assertThat(thrown)
            .describedAs("handshake exception is not modified")
            .isSameAs(exception);

        // protocols should not change
        verify(sslSocket, never()).setEnabledProtocols(any());
    }

    private static SSLSocket mockSocket(String... enabledProtocols) {
        SSLSocket sslSocket = mock(SSLSocket.class);
        when(sslSocket.getEnabledProtocols()).thenReturn(enabledProtocols);
        return sslSocket;
    }

}
