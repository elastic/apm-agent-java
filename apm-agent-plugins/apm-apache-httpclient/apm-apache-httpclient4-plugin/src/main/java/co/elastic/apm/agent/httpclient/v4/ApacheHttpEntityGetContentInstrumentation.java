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
package co.elastic.apm.agent.httpclient.v4;

import co.elastic.apm.agent.httpclient.common.AbstractApacheHttpRequestBodyCaptureAdvice;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpEntity;

import java.io.InputStream;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ApacheHttpEntityGetContentInstrumentation extends BaseApacheHttpEntityInstrumentation {

    public static class ApacheHttpEntityGetContentAdvice extends AbstractApacheHttpRequestBodyCaptureAdvice {

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        @Advice.AssignReturned.ToReturned
        public static InputStream onExit(@Advice.This HttpEntity thiz, @Advice.Return InputStream content) {
            return maybeCaptureRequestBodyInputStream(thiz, content);
        }
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.httpclient.v4.ApacheHttpEntityGetContentInstrumentation$ApacheHttpEntityGetContentAdvice";
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("getContent")
            .and(takesArguments(0));
    }

}
