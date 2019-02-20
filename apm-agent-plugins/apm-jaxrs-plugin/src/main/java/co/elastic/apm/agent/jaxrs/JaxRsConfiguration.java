/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.jaxrs;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

/**
 * Configuration provider for the apm jax-rs plugin
 */
public class JaxRsConfiguration extends ConfigurationOptionProvider {
    private static final String JAXRS_CATEGORY = "JAX-RS";

    private final ConfigurationOption<Boolean> enableJaxrsAnnotationInheritance = ConfigurationOption.booleanOption()
        .key("enable_jaxrs_annotation_inheritance")
        .configurationCategory(JAXRS_CATEGORY)
        .tags("performance")
        .description("According to JAX-RS 2.0 spec (section 3.6 Annotation Inheritance) inheritance of class or interface annotations is not supported.\n" +
            "Thus, the agent will not recognize JAX-RS resource where the implementation class is not directly annotated with the @Path annotation.\n" +
            "However, some JAX-RS implementation allow the @Path annotation to be inherited from superclasses and interfaces.\n" +
            "If your application uses @Path annotation inheritance, set this option to `true` to allow the agent to recognize those JAX-RS resources.\n" +
            "\n" +
            "NOTE: Setting this to `true` will reduce startup performance because of class hierarchy scanning. Only use when needed")
        .dynamic(false)
        .buildWithDefault(false);


    /**
     * @return if true, the jax-rs plugin must scan for @Path annotations in the class hierarchy of classes.
     * if false, only @Path annotations on implementation classes are considered.
     */
    public boolean isEnableJaxrsAnnotationInheritance() {
        return enableJaxrsAnnotationInheritance.get();
    }
}
