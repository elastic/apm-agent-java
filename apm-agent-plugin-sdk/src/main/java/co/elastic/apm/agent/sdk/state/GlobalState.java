/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.sdk.state;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotating a class with {@link GlobalState} excludes it from being loaded by the plugin class loader.
 * It will instead be loaded by the agent class loader, which is currently the bootstrap class loader, although that is subject to change.
 * This will make it's static variables globally available instead of being local to the plugin class loader.
 * <p>
 * Normally, all classes within an instrumentation plugin are loaded from a dedicated class loader
 * that is the child of the class loader that contains the instrumented classes.
 * If there are multiple class loaders that are instrumented with a given instrumentation plugin,
 * the instrumentation classes will also be loaded by multiple class loaders.
 * The effect of that is that state added to static variables in one class loader does not affect the static variable in other class loaders.
 * </p>
 * <p>
 * An alternative to this is {@link GlobalVariables} which can be used to make individual variables scoped globally.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GlobalState {
}
