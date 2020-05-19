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
package co.elastic.apm.agent.dubbo;

import co.elastic.apm.agent.dubbo.api.DubboTestApi;
import co.elastic.apm.agent.dubbo.api.exception.BizException;
import co.elastic.apm.agent.dubbo.api.impl.DubboTestApiImpl;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.MethodConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

public class ApacheDubboInstrumentationTest extends AbstractDubboInstrumentationTest {

    private static ReferenceConfig<DubboTestApi> referenceConfig;

    private static ServiceConfig<DubboTestApi> serviceConfig;

    @Override
    protected DubboTestApi buildDubboTestApi() {
        ApplicationConfig providerAppConfig = new ApplicationConfig();
        providerAppConfig.setName("dubbo-demo");

        ProtocolConfig protocolConfig = new ProtocolConfig();
        protocolConfig.setName("dubbo");
        protocolConfig.setPort(getPort());
        protocolConfig.setThreads(10);

        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setAddress("N/A");

        serviceConfig = new ServiceConfig<>();
        serviceConfig.setApplication(providerAppConfig);
        serviceConfig.setProtocol(protocolConfig);
        serviceConfig.setInterface(DubboTestApi.class);
        serviceConfig.setRef(new DubboTestApiImpl());
        serviceConfig.setRegistry(registryConfig);
        serviceConfig.export();

        referenceConfig = new ReferenceConfig<>();
        referenceConfig.setApplication(providerAppConfig);
        referenceConfig.setInterface(DubboTestApi.class);
        referenceConfig.setUrl("dubbo://localhost:" + getPort());
        referenceConfig.setTimeout(3000);

        List<MethodConfig> methodConfigList = new LinkedList<>();
        referenceConfig.setMethods(methodConfigList);

        MethodConfig asyncConfig = new MethodConfig();
        asyncConfig.setName("async");
        asyncConfig.setAsync(true);
        methodConfigList.add(asyncConfig);

        MethodConfig asyncNoReturnConfig = new MethodConfig();
        asyncNoReturnConfig.setName("asyncNoReturn");
        asyncNoReturnConfig.setAsync(true);
        asyncNoReturnConfig.setReturn(false);
        methodConfigList.add(asyncNoReturnConfig);

        return referenceConfig.get();
    }

    @Override
    int getPort() {
        return 20881;
    }

    @Test
    public void testAsync() throws Exception {
        String arg = "hello";
        DubboTestApi dubboTestApi = getDubboTestApi();
        String ret = dubboTestApi.async(arg);
        assertThat(ret).isNull();
        Future<Object> future = RpcContext.getContext().getFuture();
        assertThat(future).isNotNull();
        ret = (String) future.get();
        assertThat(ret).isEqualTo(arg);

        Transaction transaction = reporter.getFirstTransaction(1000);
        validateDubboTransaction(transaction, DubboTestApi.class, "async");

        assertThat(reporter.getFirstSpan(500)).isNotNull();
        List<Span> spans = reporter.getSpans();
        assertThat(spans.size()).isEqualTo(1);
    }

    @Test
    public void testAsyncException() throws Exception {
        DubboTestApi dubboTestApi = getDubboTestApi();
        String arg = "error";
        try {
            dubboTestApi.async(arg);
            Future<Object> future = RpcContext.getContext().getFuture();
            assertThat(future).isNotNull();
            future.get();
        } catch (Exception e) {
            // exception from Future will be wrapped as RpcException by dubbo implementation
            assertThat(e.getCause() instanceof BizException).isTrue();
            Transaction transaction = reporter.getFirstTransaction(1000);
            assertThat(reporter.getFirstSpan(500)).isNotNull();
            List<Span> spans = reporter.getSpans();
            assertThat(spans.size()).isEqualTo(1);

            List<ErrorCapture> errors = reporter.getErrors();
            assertThat(errors.size()).isEqualTo(2);
            for (ErrorCapture error : errors) {
                Throwable t = error.getException();
                assertThat(t instanceof BizException).isTrue();
            }
            return;
        }
        throw new RuntimeException("not ok");
    }

    @Test
    public void testAsyncByFuture() throws Exception {
        DubboTestApi dubboTestApi = getDubboTestApi();
        String arg = "hello";
        CompletableFuture<String> future = dubboTestApi.asyncByFuture(arg);
        assertThat(future).isNotNull();
        assertThat(future.get()).isEqualTo(arg);

        Transaction transaction = reporter.getFirstTransaction(1000);
        validateDubboTransaction(transaction, DubboTestApi.class, "asyncByFuture");

        assertThat(reporter.getFirstSpan(500)).isNotNull();
        Thread.sleep(1000); // wait 1s
        List<Span> spans = reporter.getSpans();
        assertThat(spans.size()).isEqualTo(2);
    }

    @Test
    public void testAsyncByFutureException() throws Exception {
        DubboTestApi dubboTestApi = getDubboTestApi();
        String arg = "error";
        CompletableFuture<String> future = dubboTestApi.asyncByFuture(arg);
        try {
            future.get();
        } catch (Exception e) {
            Transaction transaction = reporter.getFirstTransaction(1000);
            validateDubboTransaction(transaction, DubboTestApi.class, "asyncByFuture");

            assertThat(reporter.getFirstSpan(500)).isNotNull();
            Thread.sleep(1000); // wait reporter data 1s
            List<Span> spans = reporter.getSpans();
            assertThat(spans.size()).isEqualTo(2);

            List<ErrorCapture> errors = reporter.getErrors();
            assertThat(errors).hasSize(2);
            for (ErrorCapture error : errors) {
                assertThat(error.getException() instanceof BizException).isTrue();
            }
            return;
        }
        throw new RuntimeException("not ok");
    }


    @Test
    public void testAsyncByAsyncContext() throws Exception {
        DubboTestApi dubboTestApi = getDubboTestApi();
        String arg = "hello";
        String ret = dubboTestApi.asyncByAsyncContext(arg);
        assertThat(ret).isEqualTo(arg);

        Transaction transaction = reporter.getFirstTransaction(1000);
        validateDubboTransaction(transaction, DubboTestApi.class, "asyncByAsyncContext");

        assertThat(reporter.getFirstSpan(500)).isNotNull();
        List<Span> spans = reporter.getSpans();
        assertThat(spans.size()).isEqualTo(2);
    }

    @Test
    public void testAsyncByAsyncContextException() throws Exception {
        DubboTestApi dubboTestApi = getDubboTestApi();
        try {
            dubboTestApi.asyncByAsyncContext("error");
        } catch (BizException e) {
            Transaction transaction = reporter.getFirstTransaction(1000);
            validateDubboTransaction(transaction, DubboTestApi.class, "asyncByAsyncContext");

            assertThat(reporter.getFirstSpan(5000)).isNotNull();
            List<Span> spans = reporter.getSpans();
            assertThat(spans.size()).isEqualTo(2);

            List<ErrorCapture> errors = reporter.getErrors();
            assertThat(errors).hasSize(2);
            for (ErrorCapture error : errors) {
                assertThat(error.getException() instanceof BizException).isTrue();
            }
            return;
        }
        throw new RuntimeException("not ok");
    }
}
