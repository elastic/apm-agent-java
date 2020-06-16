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
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.MethodConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.config.ServiceConfig;
import com.alibaba.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class AlibabaDubboInstrumentationTest extends AbstractDubboInstrumentationTest {

    private static ReferenceConfig<DubboTestApi> referenceConfig;

    private static ServiceConfig<DubboTestApi> serviceConfig;

    @Override
    protected DubboTestApi buildDubboTestApi() {
        ApplicationConfig providerAppConfig = new ApplicationConfig();
        providerAppConfig.setName("dubbo-provider");

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

        ApplicationConfig consumerApp = new ApplicationConfig();
        consumerApp.setName("dubbo-consumer");
        referenceConfig = new ReferenceConfig<>();
        referenceConfig.setApplication(consumerApp);
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
        return 20880;
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
            fail("expected to get exception from async dubbo call");
        } catch (Exception e) {
            // exception from Future will be wrapped as RpcException by dubbo implementation
            assertThat(e.getCause()).isInstanceOf(BizException.class);
            Transaction transaction = reporter.getFirstTransaction(1000);
            assertThat(transaction).isNotNull();
            assertThat(reporter.getFirstSpan(500)).isNotNull();
            List<Span> spans = reporter.getSpans();
            assertThat(spans.size()).isEqualTo(1);

            List<ErrorCapture> errors = reporter.getErrors();
            assertThat(errors.size()).isEqualTo(2);
            for (ErrorCapture error : errors) {
                assertThat(error.getException()).isInstanceOf(BizException.class);
            }
        }
    }
}
