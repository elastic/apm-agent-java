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
package co.elastic.apm.agent.dubbo.helper;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.impl.transaction.Transaction;

import java.net.InetSocketAddress;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

@VisibleForAdvice
public class DubboHelper {

    @VisibleForAdvice
    public static ElasticApmTracer tracer;

    private static String EXTERNAL_TYPE = "external";

    private static String DUBBO_SUBTYPE = "dubbo";

    public static final String PROVIDER_SERVICE_NAME_KEY = "elastic-apm-dubbo-provider";

    public static void init(ElasticApmTracer tracer) {
        DubboHelper.tracer = tracer;
    }

    @VisibleForAdvice
    public static String buildDubboRequestName(DubboApiInfo apiInfo) {
        Class<?>[] paramClasses = apiInfo.getParamClasses();
        String paramsSign = "";
        if (paramClasses != null && paramClasses.length > 0) {
            StringBuilder paramSignBuilder = new StringBuilder(paramClasses[0].getSimpleName());
            for (int i = 1; i < paramClasses.length; i++) {
                paramSignBuilder.append(",").append(paramClasses[i].getSimpleName());
            }
            paramsSign = paramSignBuilder.toString();
        }

        String requestName = apiInfo.getApiClass().getName() + "." + apiInfo.getMethodName() + "(" + paramsSign + ")";
        String version = apiInfo.getVersion();
        if (version != null && version.length() > 0) {
            requestName += " version=" + version;
        }

        return requestName;
    }

    @VisibleForAdvice
    public static Span createConsumerSpan(DubboApiInfo apiInfo, InetSocketAddress remoteAddress) {
        TraceContextHolder<?> traceContext = DubboHelper.tracer.getActive();
        if (traceContext == null) {
            return null;
        }
        Span span = traceContext.createExitSpan();
        if (span == null) {
            return null;
        }

        span.withType(EXTERNAL_TYPE)
            .withSubtype(DUBBO_SUBTYPE)
            .withName(buildDubboRequestName(apiInfo));
        Destination destination = span.getContext().getDestination();
        destination.withAddress(remoteAddress.getHostName()).withPort(remoteAddress.getPort());

        Destination.Service service = destination.getService();
        service.withType(EXTERNAL_TYPE).withResource(DUBBO_SUBTYPE);

        return span.activate();
    }

    @VisibleForAdvice
    public static void fillTransaction(Transaction transaction, DubboApiInfo dubboApiInfo) {
        transaction.withName(buildDubboRequestName(dubboApiInfo));
        transaction.withType("dubbo");
        transaction.activate();
    }

    public static boolean isBizException(Class<?> interfaceClass, Class<?> exp) {
        String apiJarFile = getJarFile(interfaceClass);
        if (apiJarFile == null) {
            return false;
        }
        return apiJarFile.equals(getJarFile(exp));
    }

    public static String getJarFile(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        ProtectionDomain domain = clazz.getProtectionDomain();
        if (domain == null) {
            return null;
        }
        CodeSource source = domain.getCodeSource();
        if (source == null) {
            return null;
        }
        URL location = source.getLocation();
        if (location == null) {
            return null;
        }
        return location.getFile();
    }

    public static void doCapture(Object[] args, Throwable t, Object returnValue) {
        Transaction transaction = tracer.currentTransaction();
        if (transaction == null) {
            return;
        }
        boolean hasError = t != null;
        CoreConfiguration coreConfig = tracer.getConfig(CoreConfiguration.class);
        CoreConfiguration.EventType captureBody = coreConfig.getCaptureBody();
        if (CoreConfiguration.EventType.OFF.equals(captureBody) ||
            (CoreConfiguration.EventType.ERRORS.equals(captureBody) && !hasError)) {
            return;
        }

        captureArgs(transaction, args);
        if (t != null) {
            transaction.addCustomContext("throw", t.toString());
        } else {
            transaction.addCustomContext("return", returnValue.toString());
        }
    }

    public static void captureArgs(Transaction transaction, Object[] args) {
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                transaction.addCustomContext("arg-" + i, args[i].toString());
            }
        }
    }
}
