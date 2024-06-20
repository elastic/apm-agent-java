package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.tracer.Span;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;

public class RequestBodyRecordingOutputStream extends OutputStream {

    public static final int MAX_LENGTH = 1024;

    private final OutputStream delegate;

    @Nullable
    private Span<?> clientSpan;

    public RequestBodyRecordingOutputStream(OutputStream delegate, Span<?> clientSpan) {
        this.delegate = delegate;
        clientSpan.incrementReferences();
        this.clientSpan = clientSpan;
    }

    @Override
    public void write(int b) throws IOException {
        try {
            appendToBody((char) b);
        } finally {
            delegate.write(b);
        }
    }

    protected void appendToBody(char b) {
        if (clientSpan != null) {
            StringBuilder body = clientSpan.getContext().getHttp().getRequestBody(true);
            if (body.length() < MAX_LENGTH) {
                body.append(b);
            }
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        try {
            appendToBody(b, 0, b.length);
        } finally {
            delegate.write(b);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        try {
            appendToBody(b, off, len);
        } finally {
            delegate.write(b, off, len);
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
    public void close() throws IOException {
        try {
            releaseSpan();
        } finally {
            delegate.close();
        }
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }
}
