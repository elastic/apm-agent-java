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
package co.elastic.apm.agent.benchmark;

import co.elastic.apm.agent.benchmark.sql.BlackholeConnection;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.report.Reporter;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
@State(Scope.Benchmark)
public abstract class ElasticApmContinuousBenchmark extends AbstractMockApmServerBenchmark {

    protected HttpServlet httpServlet;

    public ElasticApmContinuousBenchmark(boolean apmEnabled) {
        super(apmEnabled);
    }

    @Override
    public void setUp(Blackhole blackhole) throws IOException {
        super.setUp(blackhole);
        final BlackholeConnection blackholeConnection = BlackholeConnection.INSTANCE;
        blackholeConnection.init(blackhole);
        httpServlet = new ElasticApmContinuousBenchmark.BenchmarkingServlet(blackholeConnection, tracer, blackhole);
        System.getProperties().put(Reporter.class.getName(), tracer.getReporter());
    }

    @State(Scope.Thread)
    public static class RequestState {

        HttpServletRequest request;
        HttpServletResponse response;

        @Setup
        public void setUp() {
            request = createRequest();
            response = createResponse();
        }

        private HttpServletRequest createRequest() {
            final MockHttpServletRequest req = new MockHttpServletRequest("GET", "http://30thh.loc:8480/app/test%3F/a%3F+b;jsessionid=S+ID");
            req.setHeader("Authorization", "Basic foo:bar");
            req.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
            req.setHeader("Accept-Encoding", "gzip, deflate, br");
            req.setHeader("Accept-Language", "en-GB,en;q=0.9,en-US;q=0.8,de;q=0.7");
            req.setHeader("Cache-Control", "max-age=0");
            req.setHeader("Connection", "keep-alive");
            req.setParameter("p 1", "c d");
            req.setContextPath("/app");
            req.setPathInfo("/a?+b");
            req.setQueryString("p+1=c+d&p+2=e+f");
            req.setRequestURI("/app/test%3F/a%3F+b;jsessionid=S+ID");
            req.setServletPath("/test?");
            req.setServerName("30thh.loc");
            return req;
        }

        private HttpServletResponse createResponse() {
            return new MockHttpServletResponse();
        }
    }

    private static class BenchmarkingServlet extends HttpServlet {

        private final Connection connection;
        private final Reporter reporter;
        private final Blackhole blackhole;

        private BenchmarkingServlet(Connection connection, ElasticApmTracer tracer, Blackhole blackhole) {
            this.connection = connection;
            this.reporter = tracer.getReporter();
            this.blackhole = blackhole;
        }

        @Override
        public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException {
            try {
                final PreparedStatement preparedStatement = connection
                    .prepareStatement("SELECT * FROM ELASTIC_APM WHERE foo=?");
                preparedStatement.setInt(1, 1);
                blackhole.consume(preparedStatement.executeQuery());
                // makes sure the jdbc query and the reporting can't be eliminated by JIT
                // setting it as the http status code so that there are no allocations necessary
                // for example converting to string
                response.setStatus((int) reporter.getDropped());
            } catch (Exception e) {
                throw new ServletException(e);
            }
        }
    }
}
