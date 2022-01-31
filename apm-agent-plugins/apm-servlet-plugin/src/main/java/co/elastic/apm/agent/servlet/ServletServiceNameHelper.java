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
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@GlobalState
public class ServletServiceNameHelper {

    public static final WeakMap<ClassLoader, Boolean> nameInitialized = WeakConcurrent.buildMap();
    private static final Logger logger = LoggerFactory.getLogger(ServletServiceNameHelper.class);

    // this makes sure service name discovery also works when attaching at runtime
    public static <ServletRequest, ServletResponse, HttpServletRequest, HttpServletResponse, ServletContext> void determineServiceName(
        ServletApiAdapter<ServletRequest, ServletResponse, HttpServletRequest, HttpServletResponse, ServletContext> helper,
        HttpServletRequest request,
        Tracer tracer) {

        ServletContext servletContext = helper.getServletContext(request);
        if (servletContext == null) {
            return;
        }
        ClassLoader servletContextClassLoader = helper.getClassLoader(servletContext);
        if (servletContextClassLoader == null || nameInitialized.putIfAbsent(servletContextClassLoader, Boolean.TRUE) != null) {
            return;
        }
        String servletContextName = helper.getServletContextName(servletContext);
        String contextPath = helper.getContextPath(servletContext);

        if (logger.isDebugEnabled()) {
            logger.debug("Inferring service name for class loader [{}] based on servlet context path `{}` and request context path `{}`",
                servletContextClassLoader,
                servletContextName,
                contextPath
            );
        }

        ServiceInfo fromWarManifest = ServiceInfo.fromManifest(getManifest(helper, servletContext));
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
        tracer.overrideServiceInfoForClassLoader(servletContextClassLoader, fromWarManifest.withFallback(fromContextName).withFallback(fromContextPath));
    }

    @Nullable
    public static <ServletRequest, ServletResponse, HttpServletRequest, HttpServletResponse, ServletContext> Manifest getManifest(
        ServletApiAdapter<ServletRequest, ServletResponse, HttpServletRequest, HttpServletResponse, ServletContext> helper,
        ServletContext servletContext) {

        try (InputStream manifestStream = helper.getResourceAsStream(servletContext, "/" + JarFile.MANIFEST_NAME)) {
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
