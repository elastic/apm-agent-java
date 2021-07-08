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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;

import javax.annotation.Nullable;

public class TransactionNameUtils {

    private static final WebConfiguration webConfig = GlobalTracer.requireTracerImpl().getConfig(WebConfiguration.class);

    public static void setTransactionNameByServletClass(@Nullable String httpMethod, @Nullable Class<?> servletClass, @Nullable StringBuilder transactionName) {
        if (servletClass == null || transactionName == null) {
            return;
        }
        String servletClassName = servletClass.getName();
        transactionName.append(servletClassName, servletClassName.lastIndexOf('.') + 1, servletClassName.length());

        if (httpMethod == null) {
            return;
        }
        transactionName.append('#');
        switch (httpMethod) {
            case "DELETE":
                transactionName.append("doDelete");
                break;
            case "HEAD":
                transactionName.append("doHead");
                break;
            case "GET":
                transactionName.append("doGet");
                break;
            case "OPTIONS":
                transactionName.append("doOptions");
                break;
            case "POST":
                transactionName.append("doPost");
                break;
            case "PUT":
                transactionName.append("doPut");
                break;
            case "TRACE":
                transactionName.append("doTrace");
                break;
            default:
                transactionName.append(httpMethod);
        }

    }

    public static void setNameFromClassAndMethod(String className, @Nullable String methodName, @Nullable StringBuilder transactionName) {
        if (transactionName == null) {
            return;
        }
        transactionName.append(className);

        if (methodName != null) {
            transactionName.append('#')
                .append(methodName);
        }
    }

    public static void setNameFromHttpRequestPath(String method, String firstPart, @Nullable String secondPart, @Nullable StringBuilder transactionName) {
        if (transactionName == null) {
            return;
        }
        WildcardMatcher groupMatcher = WildcardMatcher.anyMatch(webConfig.getUrlGroups(), firstPart, secondPart);
        if (groupMatcher != null) {
            transactionName.append(method).append(' ').append(groupMatcher);
        } else {
            transactionName.append(method).append(' ').append(firstPart);
            if (secondPart != null) {
                transactionName.append(secondPart);
            }
        }
    }

}
