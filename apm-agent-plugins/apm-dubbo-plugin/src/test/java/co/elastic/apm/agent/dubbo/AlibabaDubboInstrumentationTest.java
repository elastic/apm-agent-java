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
package co.elastic.apm.agent.dubbo;

import co.elastic.apm.agent.dubbo.api.AnotherApi;
import co.elastic.apm.agent.dubbo.api.DubboTestApi;
import co.elastic.apm.agent.dubbo.api.exception.BizException;
import co.elastic.apm.agent.dubbo.api.impl.AnotherApiImpl;
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

    @Override
    protected DubboTestApi buildDubboTestApi() {
        RegistryConfig registryConfig = createRegistryConfig();
        ApplicationConfig appConfig = createApplicationConfig();

        //build AnotherApi provider
        ProtocolConfig anotherApiProtocolConfig = createProtocolConfig(getAnotherApiPort());
        createAndExportServiceConfig(registryConfig, AnotherApi.class, new AnotherApiImpl(), appConfig, anotherApiProtocolConfig);

        //build AnotherApi consumer
        ReferenceConfig<AnotherApi> anotherApiReferenceConfig = createReferenceConfig(AnotherApi.class, appConfig, anotherApiProtocolConfig.getPort());

        // build DubboTestApi provider
        ProtocolConfig protocolConfig = createProtocolConfig(getPort());
        createAndExportServiceConfig(registryConfig, DubboTestApi.class, new DubboTestApiImpl(anotherApiReferenceConfig.get()), appConfig, protocolConfig);

        // build DubboTestApi consumer
        ReferenceConfig<DubboTestApi> testApiReferenceConfig = createReferenceConfig(DubboTestApi.class, appConfig, protocolConfig.getPort());

        List<MethodConfig> methodConfigList = new LinkedList<>();
        testApiReferenceConfig.setMethods(methodConfigList);
        MethodConfig asyncConfig = new MethodConfig();
        asyncConfig.setName("async");
        asyncConfig.setAsync(true);
        methodConfigList.add(asyncConfig);

        MethodConfig asyncNoReturnConfig = new MethodConfig();
        asyncNoReturnConfig.setName("asyncNoReturn");
        asyncNoReturnConfig.setAsync(true);
        asyncNoReturnConfig.setReturn(false);
        methodConfigList.add(asyncNoReturnConfig);

        return testApiReferenceConfig.get();
    }

    private static RegistryConfig createRegistryConfig() {
        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setAddress("N/A");
        return registryConfig;
    }

    private static ApplicationConfig createApplicationConfig() {
        ApplicationConfig appConfig = new ApplicationConfig();
        appConfig.setName("all-in-one-app");
        return appConfig;
    }

    private static ProtocolConfig createProtocolConfig(int port){
        ProtocolConfig protocolConfig = new ProtocolConfig();
        protocolConfig.setName("dubbo");
        protocolConfig.setPort(port);
        protocolConfig.setThreads(10);
        return protocolConfig;
    }

    private static <T> void createAndExportServiceConfig(RegistryConfig registryConfig,
                                                         Class<T> interfaceClass,
                                                         T interfaceImpl,
                                                         ApplicationConfig applicationConfig,
                                                         ProtocolConfig protocolConfig) {

        ServiceConfig<T> serviceConfig = new ServiceConfig<T>();
        serviceConfig.setApplication(applicationConfig);
        serviceConfig.setProtocol(protocolConfig);
        serviceConfig.setInterface(interfaceClass);
        serviceConfig.setRef(interfaceImpl);
        serviceConfig.setRegistry(registryConfig);
        serviceConfig.export();
    }

    private static <T> ReferenceConfig<T> createReferenceConfig(Class<T> interfaceClass, ApplicationConfig applicationConfig, int port) {
        ReferenceConfig<T> referenceConfig = new ReferenceConfig<>();
        referenceConfig.setApplication(applicationConfig);
        referenceConfig.setInterface(interfaceClass);
        referenceConfig.setUrl(String.format("dubbo://localhost:%d", port));
        referenceConfig.setTimeout(3000);
        return referenceConfig;
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
