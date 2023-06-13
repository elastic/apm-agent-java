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
package testapp;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

public class Main {

    private static final boolean OTEL_ON_CLASSPATH = isOtelOnClassPath();

    public static void main(String[] args) {
        System.out.println("app start");
        try {
            transaction();
        } finally {
            System.out.println("app end");
        }
    }

    private static void transaction() {
        System.out.println("start transaction");
        checkCurrentSpanVisibleThroughOTel();
        span();
        checkCurrentSpanVisibleThroughOTel();
        System.out.println("end transaction");

    }

    private static void span() {
        System.out.println("start span");
        checkCurrentSpanVisibleThroughOTel();
        System.out.println("end span");
    }

    private static void checkCurrentSpanVisibleThroughOTel() {
        if (OTEL_ON_CLASSPATH) {
            //Why is this wrapping in a Runnable required?
            //Main is written to be executed with and without Otel on the classpath
            //If Main would contain direct references to Otel, it would fail to load when running without
            // Otel on the classpath.
            //This problem is prevented by doing the actual OTel accesses in an anonymous class, which is
            // never loaded when Otel is not on the classpath.
            new Runnable() {
                @Override
                public void run() {
                    SpanContext spanContext = Span.current().getSpanContext();
                    if (!spanContext.isValid()) {
                        System.out.println("no active OTel Span");
                    } else {
                        System.out.printf("active span ID = %s, trace ID = %s%n", spanContext.getSpanId(), spanContext.getTraceId());
                    }
                }
            }.run();
        }
    }

    private static boolean isOtelOnClassPath() {
        try {
            Class.forName("io.opentelemetry.api.trace.SpanContext");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

}
