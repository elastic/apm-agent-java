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
package co.elastic.apm.agent.dubbo;

import co.elastic.apm.agent.dubbo.advice.AlibabaMonitorFilterAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import com.alibaba.dubbo.monitor.support.MonitorFilter;
import com.alibaba.dubbo.remoting.exchange.ResponseFuture;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.protocol.dubbo.filter.FutureFilter;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments
 * <ul>
 *     <li>{@link MonitorFilter#invoke(Invoker, Invocation)}</li>
 *     <li>{@link FutureFilter#invoke(Invoker, Invocation)}</li>
 * </ul>
 * <p>
 * Starting the transactions/spans at the {@link FutureFilter} level makes sure the span is still active when
 * </p>
 * {@link FutureFilter#asyncCallback} is called.
 * This enables the {@link AlibabaResponseFutureInstrumentation} on {@link ResponseFuture#setCallback}
 * to associate the callbackm with the span.
 * <p>
 * However, the {@link FutureFilter} is not always in the list of filters,
 * so also instrumenting {@link MonitorFilter} which is always called.
 * </p>
 * <p>
 * The instrumentation makes sure that only the first filter starts a transaction/span
 * </p>
 * <p>
 * We can't just instrument all filters, as some don't have the full {@link com.alibaba.dubbo.rpc.RpcContext}.
 * </p>
 */
public class AlibabaMonitorFilterInstrumentation extends AbstractAlibabaDubboInstrumentation {

    public AlibabaMonitorFilterInstrumentation(ElasticApmTracer tracer) {
        AlibabaMonitorFilterAdvice.init(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.alibaba.dubbo.rpc.protocol.dubbo.filter.FutureFilter")
            .or(nameEndsWith("com.alibaba.dubbo.monitor.support.MonitorFilter"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("invoke")
            .and(takesArgument(0, named("com.alibaba.dubbo.rpc.Invoker")))
            .and(takesArgument(1, named("com.alibaba.dubbo.rpc.Invocation")));
    }

    @Override
    public Class<?> getAdviceClass() {
        return AlibabaMonitorFilterAdvice.class;
    }

}
