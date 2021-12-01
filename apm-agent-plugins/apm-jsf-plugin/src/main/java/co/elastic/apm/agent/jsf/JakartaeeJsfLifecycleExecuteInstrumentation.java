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
package co.elastic.apm.agent.jsf;

import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class JakartaeeJsfLifecycleExecuteInstrumentation extends AbstractJsfLifecycleExecuteInstrumentation {

    @Override
    String lifecycleClassName() {
        return "jakarta.faces.lifecycle.Lifecycle";
    }

    @Override
    String facesContextClassName() {
        return "jakarta.faces.context.FacesContext";
    }

    public static class AdviceClass extends BaseExecuteAdvice {

        @Nullable
        @SuppressWarnings("Duplicates")
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object createExecuteSpan(@Advice.Argument(0) @Nonnull FacesContext facesContext) {
            boolean withExternalContext = false;
            String requestServletPath = null;
            String requestPathInfo = null;
            ExternalContext externalContext = facesContext.getExternalContext();
            if (externalContext != null) {
                withExternalContext = true;
                requestServletPath = externalContext.getRequestServletPath();
                requestPathInfo = externalContext.getRequestPathInfo();
            }
            return createAndActivateSpan(withExternalContext, requestServletPath, requestPathInfo);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void endExecuteSpan(@Advice.Enter @Nullable Object span,
                                          @Advice.Thrown @Nullable Throwable t) {
            endAndDeactivateSpan(span, t);
        }
    }
}
