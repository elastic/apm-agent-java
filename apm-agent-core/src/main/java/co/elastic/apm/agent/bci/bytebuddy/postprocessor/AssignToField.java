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
package co.elastic.apm.agent.bci.bytebuddy.postprocessor;

import net.bytebuddy.dynamic.TargetType;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AssignToField {
    /**
     * Returns the name of the field.
     *
     * @return The name of the field.
     */
    String value();

    /**
     * Returns the type that declares the field that should be mapped to the annotated parameter. If this property
     * is set to {@code void}, the field is looked up implicitly within the instrumented class's class hierarchy.
     * The value can also be set to {@link TargetType} in order to look up the type on the instrumented type.
     *
     * @return The type that declares the field, {@code void} if this type should be determined implicitly or
     * {@link TargetType} for the instrumented type.
     */
    Class<?> declaringType() default Void.class;

    /**
     * The typing that should be applied when assigning the field value.
     *
     * @return The typing to apply upon assignment.
     */
    Assigner.Typing typing() default Assigner.Typing.STATIC;

    /**
     * Used in combination with {@link AssignTo} to select the index of the returned {@code Object[]} that should be used for the assignment.
     *
     * @return the index of the {@code Object[]} that should be used for the assignment.
     */
    int index() default -1;
}
