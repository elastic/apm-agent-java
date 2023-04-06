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
package co.elastic.apm.agent.jaxrs;

import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.util.VersionUtils;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;

public class JakartaeeJaxRsTransactionNameInstrumentation extends JaxRsTransactionNameInstrumentation {
    public JakartaeeJaxRsTransactionNameInstrumentation(Tracer tracer) {
        super(tracer);
    }

    @Override
    String pathClassName() {
        return "jakarta.ws.rs.Path";
    }

    public static class AdviceClass extends BaseAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void setTransactionName(@SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature,
                                              @JaxRsOffsetMappingFactory.JaxRsPath @Nullable String pathAnnotationValue) {
            setTransactionName(signature, pathAnnotationValue, VersionUtils.getVersion(jakarta.ws.rs.GET.class, "jakarta.ws.rs", "jakarta.ws.rs-api"));
        }
    }
}
