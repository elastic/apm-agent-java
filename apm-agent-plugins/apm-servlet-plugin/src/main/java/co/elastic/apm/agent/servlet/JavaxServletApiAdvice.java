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

import co.elastic.apm.agent.servlet.adapter.JavaxServletApiAdapter;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class JavaxServletApiAdvice extends ServletApiAdvice {

    private static final JavaxServletApiAdapter adapter = JavaxServletApiAdapter.get();

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnterServletService(@Advice.Argument(0) ServletRequest servletRequest) {
        return onServletEnter(adapter, servletRequest);
    }


    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitServletService(@Advice.Argument(0) ServletRequest servletRequest,
                                            @Advice.Argument(1) ServletResponse servletResponse,
                                            @Advice.Enter @Nullable Object transactionOrScopeOrSpan,
                                            @Advice.Thrown @Nullable Throwable t,
                                            @Advice.This Object thiz) {
        onExitServlet(adapter, servletRequest, servletResponse, transactionOrScopeOrSpan, t, thiz);
    }
}
