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
 *         {@link co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignToArgument}:
 *         Substitute of {@link net.bytebuddy.asm.Advice.Argument#readOnly()}.
 *     </li>
 *     <li>
 *         {@link co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignToField}:
 *         Substitute of {@link net.bytebuddy.asm.Advice.FieldValue#readOnly()}.
 *     </li>
 *     <li>
 *         {@link co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignToReturn}:
 *         Substitute of {@link net.bytebuddy.asm.Advice.Return#readOnly()}.
 *     </li>
 *     <li>
 *         {@link co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignTo}:
 *         Substitute of binding multiple values in a single method.
 *         Works by returning an {@code Object[]} from the advice method.
 *     </li>
 * </ul>
 */
@NonnullApi
package co.elastic.apm.agent.bci.bytebuddy.postprocessor;

import co.elastic.apm.agent.annotation.NonnullApi;
