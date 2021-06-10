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
package co.elastic.apm.agent.bci;

import net.bytebuddy.asm.Advice;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A marker annotation which indicates that the annotated field or method has to be public because it is called by advice methods,
 * which are inlined into other classes.
 * <p>
 * Also, when using non {@link Advice.OnMethodEnter#inline() inline}d advices,
 * the signature of the advice method,
 * as well as the advice class itself need to be public.
 * </p>
 * @deprecated This annotation is not needed for {@link TracerAwareInstrumentation#indyPlugin()}  indy plugins}.
 * That's because indy plugins never use {@link Advice.OnMethodEnter#inline() inline}d advices.
 */
@Deprecated
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface VisibleForAdvice {
}
