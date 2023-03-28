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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.servlet.helper.AsyncContextAdviceHelper;
import co.elastic.apm.agent.servlet.helper.JakartaAsyncContextAdviceHelper;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;

public class JakartaAsyncInstrumentation {

    /**
     * Matches
     * <ul>
     * <li>{@link ServletRequest#startAsync()}</li>
     * <li>{@link ServletRequest#startAsync(ServletRequest, ServletResponse)}</li>
     * </ul>
     */
    public static class JakartaStartAsyncInstrumentation extends AsyncInstrumentation.StartAsyncInstrumentation {

        @Override
        public Constants.ServletImpl getImplConstants() {
            return Constants.ServletImpl.JAKARTA;
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.servlet.JakartaAsyncInstrumentation$JakartaStartAsyncInstrumentation$JakartaStartAsyncAdvice";
        }

        public static class JakartaStartAsyncAdvice {
            private static final AsyncContextAdviceHelper<AsyncContext> asyncHelper = new JakartaAsyncContextAdviceHelper(GlobalTracer.get());

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void onExitStartAsync(@Advice.Return @Nullable AsyncContext asyncContext) {
                if (asyncContext == null) {
                    return;
                }
                asyncHelper.onExitStartAsync(asyncContext);
            }
        }
    }

    public static class JakartaAsyncContextInstrumentation extends AsyncInstrumentation.AsyncContextInstrumentation {

        @Override
        public Constants.ServletImpl getImplConstants() {
            return Constants.ServletImpl.JAKARTA;
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.servlet.AsyncInstrumentation$AsyncContextInstrumentation$AsyncContextStartAdvice";
        }

    }
}
