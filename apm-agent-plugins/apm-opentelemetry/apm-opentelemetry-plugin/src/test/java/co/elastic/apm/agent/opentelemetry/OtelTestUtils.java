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
package co.elastic.apm.agent.opentelemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class OtelTestUtils {

    public static void clearGlobalOpenTelemetry() {
        try {
            Field globalInstance = GlobalOpenTelemetry.class.getDeclaredField("globalOpenTelemetry");
            globalInstance.setAccessible(true);
            globalInstance.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void resetElasticOpenTelemetry() {
        OpenTelemetry globalOtel = GlobalOpenTelemetry.get();
        if (globalOtel.getClass().getName().startsWith("co.elastic.apm.agent")) {
            try {
                String adviceName = GlobalOpenTelemetryInstrumentation.GlobalOpenTelemetryAdvice.class.getName();
                Class<?> adviceClass = globalOtel.getClass().getClassLoader().loadClass(adviceName);
                Method resetElasticOpenTelemetryForTests = adviceClass.getDeclaredMethod("resetElasticOpenTelemetryForTests");
                resetElasticOpenTelemetryForTests.setAccessible(true);
                resetElasticOpenTelemetryForTests.invoke(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
