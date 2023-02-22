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
package co.elastic.apm.agent.sparkjava;

import co.elastic.apm.agent.sdk.utils.TransactionNameUtils;
import co.elastic.apm.agent.sdk.utils.VersionUtils;
import co.elastic.apm.plugin.spi.GlobalTracer;
import co.elastic.apm.plugin.spi.Tracer;
import co.elastic.apm.plugin.spi.Transaction;
import co.elastic.apm.plugin.spi.WebConfiguration;
import net.bytebuddy.asm.Advice;
import spark.Route;
import spark.routematch.RouteMatch;

import static co.elastic.apm.plugin.spi.AbstractSpan.PRIO_LOW_LEVEL_FRAMEWORK;

public class RoutesAdvice {

    private static final Tracer tracer = GlobalTracer.get();

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExitFind(@Advice.Return RouteMatch routeMatch) {
        Transaction<?> transaction = tracer.currentTransaction();
        if (transaction == null || routeMatch == null) {
            return;
        }

        String method = routeMatch.getHttpMethod().name().toUpperCase();
        TransactionNameUtils.setNameFromHttpRequestPath(method,
            routeMatch.getMatchUri(),
            transaction.getAndOverrideName(PRIO_LOW_LEVEL_FRAMEWORK + 1),
            tracer.getConfig(WebConfiguration.class).getUrlGroups());

        transaction.setFrameworkName("Spark");
        transaction.setFrameworkVersion(VersionUtils.getVersion(Route.class, "com.sparkjava", "spark-core"));
    }
}
