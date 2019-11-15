package co.elastic.apm.agent.report;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpUtilsTest {

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

}
