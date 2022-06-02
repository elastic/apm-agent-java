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

import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;

public abstract class JavaxServletVersionInstrumentation {

    public static class JavaxInit extends ServletVersionInstrumentation.Init {

        @Override
        public Constants.ServletImpl getImplConstants() {
            return Constants.ServletImpl.JAVAX;
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            @SuppressWarnings("Duplicates") // duplication is fine here as it allows to inline code
            public static void onEnter(@Advice.Argument(0) @Nullable ServletConfig servletConfig) {
                if (isLogEnabled()) {
                    logServletVersion(JavaxUtil.getInfoFromServletContext(servletConfig));
                }
            }
        }

    }

    public static class JavaxService extends ServletVersionInstrumentation.Service {

        @Override
        public Constants.ServletImpl getImplConstants() {
            return Constants.ServletImpl.JAVAX;
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void onEnter(@Advice.This Servlet servlet) {
                if (isLogEnabled()) {
                    logServletVersion(JavaxUtil.getInfoFromServletContext(servlet.getServletConfig()));
                }
            }
        }
    }
}
