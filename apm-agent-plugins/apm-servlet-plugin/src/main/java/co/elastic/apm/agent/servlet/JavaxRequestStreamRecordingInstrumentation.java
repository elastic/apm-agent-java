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

import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.servlet.helper.JavaxRecordingServletInputStreamWrapper;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import javax.servlet.ServletInputStream;

public class JavaxRequestStreamRecordingInstrumentation extends RequestStreamRecordingInstrumentation {

    @Override
    public Constants.ServletImpl getImplConstants() {
        return Constants.ServletImpl.JAVAX;
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.servlet.JavaxRequestStreamRecordingInstrumentation$GetInputStreamAdvice";
    }

    public static class GetInputStreamAdvice {

        private static final CallDepth callDepth = CallDepth.get(GetInputStreamAdvice.class);

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onReadEnter(@Advice.This Object thiz) {
            callDepth.increment();
        }

        @Nullable
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
        public static ServletInputStream afterGetInputStream(@Advice.Return @Nullable ServletInputStream inputStream) {
            if (callDepth.isNestedCallAndDecrement() || inputStream == null) {
                return inputStream;
            }
            final Transaction transaction = tracer.currentTransaction();
            // only wrap if the body buffer has been initialized via ServletTransactionHelper.startCaptureBody
            if (transaction != null && transaction.getContext().getRequest().getBodyBuffer() != null) {
                return new JavaxRecordingServletInputStreamWrapper(transaction.getContext().getRequest(), inputStream);
            } else {
                return inputStream;
            }
        }
    }
}
