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
package co.elastic.apm.agent.dubbo;

import co.elastic.apm.agent.dubbo.advice.ApacheMonitorFilterAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class ApacheMonitorFilterInstrumentation extends AbstractDubboInstrumentation {

    public ApacheMonitorFilterInstrumentation(ElasticApmTracer tracer) {
        ApacheMonitorFilterAdvice.init(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.dubbo.monitor.support.MonitorFilter");
    }

    /**
     * {@link org.apache.dubbo.monitor.support.MonitorFilter#invoke(Invoker, Invocation)}
     */
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("invoke")
            .and(takesArgument(0, named("org.apache.dubbo.rpc.Invoker")))
            .and(takesArgument(1, named("org.apache.dubbo.rpc.Invocation")))
            // makes sure we only instrument Dubbo 2.7.3+ which introduces this method
            .and(returns(hasSuperType(named("org.apache.dubbo.rpc.Result"))
                .and(declaresMethod(named("whenCompleteWithContext")
                    .and(takesArgument(0, named("java.util.function.BiConsumer")))))));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.dubbo.advice.ApacheMonitorFilterAdvice";
    }

}
