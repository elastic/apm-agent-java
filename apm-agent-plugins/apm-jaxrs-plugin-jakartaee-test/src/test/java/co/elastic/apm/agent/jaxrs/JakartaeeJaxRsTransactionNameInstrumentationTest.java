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
package co.elastic.apm.agent.jaxrs;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Application;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * Test jax-rs instrumentation
 */
public class JakartaeeJaxRsTransactionNameInstrumentationTest extends AbstractJaxRsTransactionNameInstrumentationTest {

    /**
     * @return configuration for the jersey test server. Includes all resource classes in the co.elastic.apm.agent.jaxrs.resources package.
     */
    @Override
    protected Application configure() {
        return new ResourceConfig(
            ResourceWithPath.class,
            ResourceWithPathOnInterface.class,
            ResourceWithPathOnAbstract.class,
            ProxiedClass$$$view.class,
            ProxiedClass$Proxy.class,
            ResourceWithPathOnMethod.class,
            ResourceWithPathOnMethodSlash.class,
            MethodDelegationResource.class,
            FooBarResource.class,
            EmptyPathResource.class,
            ResourceWithPathAndWithPathOnInterface.class);
    }

    public interface SuperResourceInterface {
        @GET
        String testMethod();
    }

    @Path("testInterface")
    public interface ResourceInterfaceWithPath extends SuperResourceInterface {
        String testMethod();
    }

    public interface ResourceInterfaceWithoutPath extends SuperResourceInterface {
        String testMethod();
    }

    public abstract static class AbstractResourceClassWithoutPath implements ResourceInterfaceWithoutPath {
    }

    @Path("testAbstract")
    public abstract static class AbstractResourceClassWithPath implements ResourceInterfaceWithoutPath {
    }

    @Path("testViewProxy")
    public static class ProxiedClass$$$view implements SuperResourceInterface {
        public String testMethod() {
            return "ok";
        }
    }

    @Path("testProxyProxy")
    public static class ProxiedClass$Proxy implements SuperResourceInterface {
        public String testMethod() {
            return "ok";
        }
    }

    @Path("test")
    public static class ResourceWithPath extends AbstractResourceClassWithoutPath {
        public String testMethod() {
            return "ok";
        }
    }

    @Path("methodDelegation")
    public static class MethodDelegationResource {
        @GET
        @Path("methodA")
        public String methodA() {
            methodB();
            return "ok";
        }

        @POST
        public void methodB() {
        }
    }

    @Path("/foo/")
    public static class FooResource {
        @GET
        @Path("/ignore")
        public String testMethod() {
            return "ok";
        }
    }

    public static class FooBarResource extends FooResource {
        @GET
        @Path("/bar")
        @Override
        public String testMethod() {
            return "ok";
        }
    }

    @Path("testWithPathMethod")
    public static class ResourceWithPathOnMethod extends AbstractResourceClassWithoutPath {

        @Override
        public String testMethod() {
            return "ok";
        }

        @GET
        @Path("{id}/")
        public String testMethodById(@PathParam("id") String id) {
            return "ok";
        }
    }

    @Path("testWithPathMethodSlash")
    public static class ResourceWithPathOnMethodSlash extends AbstractResourceClassWithoutPath {

        @Override
        public String testMethod() {
            return "ok";
        }

        @GET
        @Path("/{id}")
        public String testMethodById(@PathParam("id") String id) {
            return "ok";
        }
    }

    @Path("")
    public static class EmptyPathResource {
        @GET
        public String testMethod() {
            return "ok";
        }
    }

    public static class ResourceWithPathAndWithPathOnInterface implements ResourceInterfaceWithPath {
        @Override
        @GET
        @Path("test")
        public String testMethod() {
            return "ok";
        }
    }

    public static class ResourceWithPathOnAbstract extends AbstractResourceClassWithPath {
        public String testMethod() {
            return "ok";
        }
    }

    public static class ResourceWithPathOnInterface implements ResourceInterfaceWithPath {
        public String testMethod() {
            return "ok";
        }
    }
}
