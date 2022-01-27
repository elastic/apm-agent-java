package co.elastic.apm.agent.report.fake.http;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class Handler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        throw new IllegalStateException("fake handler does not allow to create connections");
    }
}
