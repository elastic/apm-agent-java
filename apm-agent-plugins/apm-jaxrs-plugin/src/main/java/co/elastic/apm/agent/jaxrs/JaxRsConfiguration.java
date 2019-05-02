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
        .tags("added[1.5.0]")
        .configurationCategory(JAXRS_CATEGORY)
        .tags("performance")
        .description(
            "By default, the agent will scan for @Path annotations on the whole class hierarchy, recognizing a class as a JAX-RS resource if the class or any of its superclasses/interfaces has a class level @Path annotation.\n" +
            "If your application does not use @Path annotation inheritance, set this property to 'false' to only scan for direct @Path annotations. This can improve the startup time of the agent.\n")
        .dynamic(false)
        .buildWithDefault(true);

    private final ConfigurationOption<Boolean> usePathAnnotationValueForTransactionName = ConfigurationOption.booleanOption()
        .key("use_path_annotation_value_for_transaction_name")
        .configurationCategory(JAXRS_CATEGORY)
        .tags("internal")
        .description(
            "By default, the agent will use ClassName#methodName for transaction name.\n" +
                "If you need use in transaction name value from @Path annotation, you should set to 'true'.\n")
        .dynamic(false)
        .buildWithDefault(false);


    /**
     * @return if true, the jax-rs plugin must scan for @Path annotations in the class hierarchy of classes.
     * if false, only @Path annotations on implementation classes are considered.
     */
    public boolean isEnableJaxrsAnnotationInheritance() {
        return enableJaxrsAnnotationInheritance.get();
    }

    public boolean isUsePathAnnotationValueForTransactionName() { return usePathAnnotationValueForTransactionName.get(); }
}
