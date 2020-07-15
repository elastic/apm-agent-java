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

/**
 * A limitation of non-{@linkplain net.bytebuddy.asm.Advice.OnMethodEnter#inline() inlined advices} is that the {@code readOnly} property
 * of annotations that bind values to advice method parameters cannot be used.
 * <p>
 * Because we make heavy use of non-inlined advices for
 * {@linkplain co.elastic.apm.agent.bci.ElasticApmInstrumentation#indyPlugin() indy plugins},
 * this package provides alternative means to bind values:
 * </p>
 * <ul>
 *     <li>
 *         {@link co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignTo.Argument}:
 *         Substitute of {@link net.bytebuddy.asm.Advice.Argument#readOnly()}.
 *     </li>
 *     <li>
 *         {@link co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignTo.Field}:
 *         Substitute of {@link net.bytebuddy.asm.Advice.FieldValue#readOnly()}.
 *     </li>
 *     <li>
 *         {@link co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignTo.Return}:
 *         Substitute of {@link net.bytebuddy.asm.Advice.Return#readOnly()}.
 *     </li>
 *     <li>
 *         {@link co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignTo}:
 *         Substitute of binding multiple values in a single method.
 *         Works by returning an {@code Object[]} from the advice method.
 *     </li>
 * </ul>
 *
 * Taking an argument assignment as an example, the resulting code looks like this when decompiled:
 * <pre>
 *     public String assignToArgument(String arg) {
 *         String var10000;
 *         try {
 *             // result of inline = false
 *             var10000 = co.elastic.apm.agent.bci.InstrumentationTest.AssignToArgumentInstrumentation.onEnter(s);
 *         } catch (Throwable var3) {
 *             // result of suppress = Throwable.class
 *             var3.printStackTrace();
 *             var10000 = null;
 *         }
 *
 *         // this is the result of the @AssignTo.Argument(0) post processor
 *         // it's just a piece of code that's executed after the advice that has access to the return value of the advice (var10000)
 *         // this assignment takes care of type conversions, according to {@link Argument#typing()}
 *         String var2 = var10000;
 *         // the null check avoids that we override the argument with null in case of an suppressed exception within the advice
 *         if (var2 != null) {
 *             // the actual assignment to the argument
 *             arg = var2;
 *         }
 *
 *         return arg;
 *     }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AssignTo {
    /**
     * Overrides an argument of the instrumented method with the object at index {@link Argument#index()}
     * of the {@code Object[]} returned from the advice.
     */
    Argument[] arguments() default {};

    /**
     * Overrides a field of the instrumented class with the object at index {@link Field#index()}
     * of the {@code Object[]} returned from the advice.
     */
    Field[] fields() default {};

    /**
     * Overrides the return value of the instrumented method with the object at index {@link Return#index()}
     * of the {@code Object[]} returned from the advice.
     */
    Return[] returns() default {};

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Argument {

        /**
         * Returns the index of the mapped parameter.
         *
         * @return The index of the mapped parameter.
         */
        int value();

        /**
         * The typing that should be applied when assigning the argument.
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

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Field {
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

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Return {

        /**
         * Determines the typing that is applied when assigning the return value.
         *
         * @return The typing to apply when assigning the annotated parameter.
         */
        Assigner.Typing typing() default Assigner.Typing.STATIC;

        /**
         * Used in combination with {@link AssignTo} to select the index of the returned {@code Object[]} that should be used for the assignment.
         *
         * @return the index of the {@code Object[]} that should be used for the assignment.
         */
        int index() default -1;
    }
}
