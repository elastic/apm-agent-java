/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.benchmark;

import co.elastic.apm.bci.ElasticApmAgent;
import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.report.Reporter;
import io.undertow.Undertow;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.SimpleSource;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

/**
 * This benchmark evaluates the overhead of recording a Servlet API-based HTTP request and a JDBC query and reporting this to a mock
 * APM-server over HTTP, including serializing the payloads to json.
 * <p>
 * This benchmark may be classified as a meso-benchmark
 * as it does not only test one component in isolation.
 * Instead,
 * it measures the combined overhead of creating a transaction from a HTTP request,
 * capturing a SQL span and reporting payloads.
 * The purpose of that is to get a better feeling of how these different activities interfere with one another and what the combined
 * overhead of the agent might look like.
 * </p>
 * <p>
 * Note that this benchmark is not using a real application server
 * but instead invokes an {@link HttpServlet} manually using mock instances of the request and response objects.
 * This aims to reduce the measurement uncertainty or clutter in terms of latency and allocations
 * otherwise introduced by the application server
 * and by sending an actual HTTP request inside the benchmark vs. just invoking a method.
 * </p>
 * <p>
 * Also note that this approach assumes that calling methods on {@link javax.servlet.http.HttpServletRequest},
 * like {@link HttpServletRequest#getHeaderNames()} or {@link HttpServletRequest#getCookies()}
 * does not allocate memory or would be called anyway by the application or framework
 * and the server implementationdoes not allocate memory when calling these methods twice.
 * But that assuption might not always be true and depends on the actual application server/servlet container in use.
 * </p>
 * <p>
 * Reporting is done using the normal mechanism,
 * which means that it does not block the application and kicks in when the max batch size is reached.
 * Note that the reporter will be under a lot of stress and won't be able to cope with the amount of transactions created.
 * Because of this, some transactions will be dropped.
 * This is preferred to the alternative which is to block the application until the report is done.
 * </p>
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public abstract class ElasticApmContinuousBenchmark extends AbstractBenchmark {

    private final boolean apmEnabled;
    protected MockHttpServletRequest request;
    protected MockHttpServletResponse response;
    protected HttpServlet httpServlet;
    private Undertow server;
    private ElasticApmTracer tracer;

    public ElasticApmContinuousBenchmark(boolean apmEnabled) {
        this.apmEnabled = apmEnabled;
    }

    @Setup
    public void setUp() throws SQLException {
        server = Undertow.builder()
            .addHttpListener(0, "127.0.0.1")
            .setHandler(exchange -> exchange.setStatusCode(200).endExchange()).build();
        server.start();
        int port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
        tracer = ElasticApmTracer.builder()
            .configurationRegistry(ConfigurationRegistry.builder()
                .addConfigSource(new SimpleSource()
                    .add(CoreConfiguration.SERVICE_NAME, "benchmark")
                    .add(CoreConfiguration.INSTRUMENT, Boolean.toString(apmEnabled))
                    .add(CoreConfiguration.ACTIVE, Boolean.toString(apmEnabled))
                    .add("server_url", "http://localhost:" + port))
                .optionProviders(ServiceLoader.load(ConfigurationOptionProvider.class))
                .build())
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
        request = createRequest();
        response = createResponse();

        Connection connection = DriverManager.getConnection("jdbc:h2:mem:test", "user", "");
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS ELASTIC_APM (FOO INT, BAR VARCHAR(255))");
        connection.createStatement().execute("INSERT INTO ELASTIC_APM (FOO, BAR) VALUES (1, 'APM')");
        httpServlet = new BenchmarkingServlet(connection, tracer);
    }

    @TearDown
    public void tearDown() {
        server.stop();
        tracer.stop();
        ElasticApmAgent.reset();
    }

    private MockHttpServletRequest createRequest() {
        final StringBuffer requestURL = new StringBuffer("http://30thh.loc:8480/app/test%3F/a%3F+b;jsessionid=S+ID");
        final Enumeration<String> headers = Collections.enumeration(Arrays.asList("Authorization",
            "Accept",
            "Accept-Encoding",
            "Accept-Language",
            "Cache-Control",
            "Connection"));

        final HashMap<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("p 1", new String[]{"c d"});
        final MockHttpServletRequest req = new MockHttpServletRequest() {
            @Override
            public StringBuffer getRequestURL() {
                return requestURL;
            }

            // MockHttpServletRequest is quite inefficient around headers; a lot of allocations happening...
            @Override
            public Enumeration<String> getHeaderNames() {
                return headers;
            }

            @Override
            public String getHeader(String name) {
                switch (name) {
                    case "Authorization":
                        return "Basic foo:bar";
                    case "Accept":
                        return "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8";
                    case "Accept-Encoding":
                        return "gzip, deflate, br";
                    case "Accept-Language":
                        return "en-GB,en;q=0.9,en-US;q=0.8,de;q=0.7";
                    case "Cache-Control":
                        return "max-age=0";
                    case "Connection":
                        return "keep-alive";
                    default:
                        return null;
                }
            }

            @Override
            public Map<String, String[]> getParameterMap() {
                return parameterMap;
            }
        };
        req.setContextPath("/app");
        req.setLocalAddr("127.0.0.1");
        req.setLocalName("30thh.loc");
        req.setLocalPort(8480);
        req.setMethod("GET");
        req.setPathInfo("/a?+b");
        req.setProtocol("HTTP/1.1");
        req.setQueryString("p+1=c+d&p+2=e+f");
        req.setRequestedSessionId("S%3F+ID");
        req.setRequestURI("/app/test%3F/a%3F+b;jsessionid=S+ID");
        req.setScheme("http");
        req.setServerName("30thh.loc");
        req.setServerPort(8480);
        req.setServletPath("/test?");
        return req;
    }

    private MockHttpServletResponse createResponse() {
        return new MockHttpServletResponse();
    }

    private static class BenchmarkingServlet extends HttpServlet {

        private final Connection connection;
        private final Reporter reporter;

        private BenchmarkingServlet(Connection connection, ElasticApmTracer tracer) {
            this.connection = connection;
            reporter = tracer.getReporter();
        }

        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException {
            try {
                final PreparedStatement preparedStatement = connection
                    .prepareStatement("SELECT * FROM ELASTIC_APM WHERE foo=?");
                preparedStatement.setInt(1, 1);
                final ResultSet resultSet = preparedStatement.executeQuery();
                int count = 0;
                while (resultSet.next()) {
                    count += resultSet.getString(1).length();
                }
                // makes sure the jdbc query and the reporting can't be eliminated by JIT
                // setting it as the http status code so that there are no allocations necessary
                response.setStatus(count + reporter.getDropped());
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }
}
