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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.configuration.ServiceInfo;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.servlet.adapter.ServletContextAdapter;
import co.elastic.apm.agent.tracer.Tracer;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@GlobalState
public class ServletServiceNameHelper {

    public static final WeakMap<ClassLoader, Boolean> nameInitialized = WeakConcurrent.buildMap();
    private static final Logger logger = LoggerFactory.getLogger(ServletServiceNameHelper.class);

    // this makes sure service name discovery also works when attaching at runtime
    public static <ServletContext> void determineServiceName(ServletContextAdapter<ServletContext> adapter,
                                                             @Nullable ServletContext servletContext,
                                                             Tracer tracer) {

        ElasticApmTracer elasticApmTracer = tracer.probe(ElasticApmTracer.class);
        if (elasticApmTracer == null) {
            return;
        }

        if (servletContext == null) {
            return;
        }
        ClassLoader servletContextClassLoader = null;

        if (!servletContext.getClass().getName().startsWith("org.apache.sling")) {
            // Apache Sling explicitly prevents us from getting the classloader through a SecurityException
            servletContextClassLoader = adapter.getClassLoader(servletContext);
        }

        if (servletContextClassLoader == null || nameInitialized.putIfAbsent(servletContextClassLoader, Boolean.TRUE) != null) {
            return;
        }
        ServiceInfo serviceInfo = detectServiceInfo(adapter, servletContext, servletContextClassLoader);
        elasticApmTracer.setServiceInfoForClassLoader(servletContextClassLoader, serviceInfo);
    }

    public static <ServletContext> ServiceInfo detectServiceInfo(ServletContextAdapter<ServletContext> adapter, ServletContext servletContext, ClassLoader servletContextClassLoader) {
        String servletContextName = adapter.getServletContextName(servletContext);
        String contextPath = adapter.getContextPath(servletContext);

        if (logger.isDebugEnabled()) {
            logger.debug("Inferring service name for class loader [{}] based on servlet context path `{}` and request context path `{}`",
                servletContextClassLoader,
                servletContextName,
                contextPath
            );
        }

        ServiceInfo fromWarManifest = ServiceInfo.fromManifest(getManifest(adapter, servletContext));
        ServiceInfo fromContextName = ServiceInfo.empty();
        if (!"application".equals(servletContextName) && !"".equals(servletContextName) && !"/".equals(servletContextName)) {
            // payara returns an empty string as opposed to null
            // spring applications which did not set spring.application.name have application as the default
            // jetty returns context path when no display name is set, which could be the root context of "/"
            // this is a worse default than the one we would otherwise choose
            fromContextName = ServiceInfo.of(servletContextName);
        }
        ServiceInfo fromContextPath = ServiceInfo.empty();
        if (contextPath != null && !contextPath.isEmpty()) {
            // remove leading slash
            fromContextPath = ServiceInfo.of(contextPath.substring(1));
        }
        return fromWarManifest.withFallback(fromContextName).withFallback(fromContextPath);
    }

    @Nullable
    private static <ServletContext> Manifest getManifest(ServletContextAdapter<ServletContext> adapter, ServletContext servletContext) {

        try (InputStream manifestStream = adapter.getResourceAsStream(servletContext, "/" + JarFile.MANIFEST_NAME)) {
            if (manifestStream == null) {
                return null;
            }
            return new Manifest(manifestStream);
        } catch (Exception e) {
            return null;
        }
    }

    // visible for testing as clearing cache is required between tests execution
    static void clearServiceNameCache() {
        nameInitialized.clear();
    }
}
