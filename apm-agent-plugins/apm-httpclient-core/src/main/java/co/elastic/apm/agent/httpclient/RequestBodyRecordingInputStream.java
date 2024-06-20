package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.tracer.Span;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RequestBodyRecordingInputStream extends InputStream {

    public static final int MAX_LENGTH = 1024;

    private final InputStream delegate;

    @Nullable
    private Span<?> clientSpan;

    public RequestBodyRecordingInputStream(InputStream delegate, Span<?> clientSpan) {
        this.delegate = delegate;
        clientSpan.incrementReferences();
        this.clientSpan = clientSpan;
    }


    protected void appendToBody(char b) {
        if (clientSpan != null) {
            StringBuilder body = clientSpan.getContext().getHttp().getRequestBody(true);
            if (body.length() < MAX_LENGTH) {
                body.append(b);
            }
        }
    }

    protected void appendToBody(byte[] b, int off, int len) {
        if (clientSpan != null) {
            StringBuilder body = clientSpan.getContext().getHttp().getRequestBody(true);
            int remaining = Math.min(MAX_LENGTH - body.length(), len);
            for (int i = 0; i < remaining; i++) {
                body.append((char) b[off + i]);
            }
        }
    }

    public void releaseSpan() {
        if (clientSpan != null) {
            clientSpan.decrementReferences();
            clientSpan = null;
        }
    }

    @Override
    public int read() throws IOException {
        int character = delegate.read();
        if (character == -1) {
            releaseSpan();
        } else {
            appendToBody((char) character);
        }
        return character;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int readBytes = delegate.read(b, off, len);
        if (readBytes == -1) {
            releaseSpan();
        } else {
            appendToBody(b, off, readBytes);
        }
        return readBytes;
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        try {
            releaseSpan();
        } finally {
            delegate.close();
        }
    }
}
