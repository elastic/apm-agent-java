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

import co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public final class Constants {
    private Constants() {
    }

    static final String SERVLET_API = "servlet-api";
    static final String SERVLET_API_DISPATCH = "servlet-api-dispatch";
    static final String SERVLET_API_ASYNC = "servlet-api-async";
    static final String SERVLET_INPUT_STREAM = "servlet-input-stream";


    public enum ServletImpl {
        JAVAX(
            "javax.servlet.Servlet",
            "javax.servlet.Filter",
            "javax.servlet.ServletRequest",
            "javax.servlet.ServletResponse",
            "javax.servlet.FilterChain",
            "javax.servlet.AsyncContext",
            "javax.servlet.http.HttpServlet",
            "javax.servlet.ServletInputStream",
            "javax.servlet.ServletConfig"),
        JAKARTA(
            "jakarta.servlet.Servlet",
            "jakarta.servlet.Filter",
            "jakarta.servlet.ServletRequest",
            "jakarta.servlet.ServletResponse",
            "jakarta.servlet.FilterChain",
            "jakarta.servlet.AsyncContext",
            "jakarta.servlet.http.HttpServlet",
            "jakarta.servlet.ServletInputStream",
            "jakarta.servlet.ServletConfig");

        private final String servletClass;
        private final String filterClass;

        private final String requestClass;
        private final String responseClass;

        private final String filterChainClass;
        private final String servletInputStreamClass;
        private final String servletConfigClass;

        // async context class has been introduced in servlet spec 3.0
        // choice of class name to use for this test does not work as expected across all application servers
        // for example, 'javax.servlet.annotation.WebServlet' annotation is not working as expected on Payara
        private final String asyncContextClass;

        // OSGi bundles might only include 'jakarta.servlet' and not 'jakarta.servlet.http' (same for `java.servlet`), hence
        // this allows to disable instrumentation as it is required for our instrumentation code.
        private final String osgiClassloaderFilterClass;

        ServletImpl(String servletClass, String filterClass, String requestClass, String responseClass, String filterChainClass,
                    String asyncContextClass, String osgiClassloaderFilterClass, String servletInputStreamClass, String servletConfigClass) {
            this.servletClass = servletClass;
            this.filterClass = filterClass;
            this.requestClass = requestClass;
            this.responseClass = responseClass;
            this.filterChainClass = filterChainClass;
            this.asyncContextClass = asyncContextClass;
            this.osgiClassloaderFilterClass = osgiClassloaderFilterClass;
            this.servletInputStreamClass = servletInputStreamClass;
            this.servletConfigClass = servletConfigClass;
        }

        public ElementMatcher.Junction<NamedElement> servletClass() {
            return named(servletClass);
        }

        public ElementMatcher.Junction<NamedElement> filterClass() {
            return named(filterClass);
        }

        public ElementMatcher.Junction<NamedElement> requestClass() {
            return named(requestClass);
        }

        public ElementMatcher.Junction<NamedElement> responseClass() {
            return named(responseClass);
        }

        public ElementMatcher.Junction<NamedElement> filterChainClass() {
            return named(filterChainClass);
        }

        public ElementMatcher.Junction<NamedElement> asyncContextClass() {
            return named(asyncContextClass);
        }

        public ElementMatcher.Junction<TypeDescription> servletInputStreamClass() {
            return named(servletInputStreamClass);
        }

        public ElementMatcher.Junction<TypeDescription> servletConfigClass() {
            return named(servletConfigClass);
        }

        public ElementMatcher.Junction<ClassLoader> getClassloaderFilter() {
            // async context class has been introduced in servlet spec 3.0
            // choice of class name to use for this test does not work as expected across all application servers
            // for example, 'javax.servlet.annotation.WebServlet' annotation is not working as expected on Payara
            return CustomElementMatchers.classLoaderCanLoadClass(asyncContextClass)
                // OSGi bundles might only include 'jakarta.servlet' and not 'jakarta.servlet.http' (same for `java.servlet`), hence
                // this allows to disable instrumentation as it is required for our instrumentation code.
                .and(CustomElementMatchers.classLoaderCanLoadClass(osgiClassloaderFilterClass));
        }
    }

}
