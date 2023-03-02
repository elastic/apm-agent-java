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
package co.elastic.apm.agent.opentelemetry.metrics.bridge;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BridgeExhaustivenessTest {

    /**
     * To avoid breaking changes, it is common to add default-methods to the opentelemetry-API.
     * These defaults however usually just do a NoOp, which we don't want for our bridge.
     * Our bridge should always delegate to the actual implementation.
     * <p>
     * Therefore, this test enforces that all bridge classes implement all default methods.
     * <p>
     * So, if the opentelemetry version is updated and new default methods are added, this test will fail.
     */
    @Test
    public void checkAllDefaultMethodsImplemented() throws Exception {
        BridgeFactoryLatest factory = new BridgeFactoryLatest();
        //we discover all bridged classes by invoking all bridgeXYZ methods on the factory
        for (Method method : BridgeFactoryLatest.class.getMethods()) {
            if (method.getName().startsWith("bridge")) {
                assertThat(method.getParameterCount()).isEqualTo(1);
                Class<?> sdkDelegateType = method.getParameters()[0].getType();

                Object bridged = method.invoke(factory, Mockito.mock(sdkDelegateType));
                Class<?> bridgeType = bridged.getClass();

                List<Class<?>> bridgeTypes = getBridgeTypes(bridged.getClass());

                for (Class<?> otelInterface : getAllOtelSuperInterfaces(bridgeType)) {
                    for (Method otelMethod : otelInterface.getMethods()) {
                        if (otelMethod.isDefault() && !methodExists(bridgeTypes, otelMethod.getName(), otelMethod.getParameterTypes())) {
                            Assertions.fail("Expected bridge type '" + bridgeType.getName() + "' to implement default method '" + otelMethod + "' from type '" + otelInterface + "'");
                        }
                    }
                }
            }
        }
    }

    private boolean methodExists(List<Class<?>> typesToCheck, String name, Class<?>[] parameterTypes) {
        for (Class<?> type : typesToCheck) {
            try {
                type.getDeclaredMethod(name, parameterTypes);
                return true;
            } catch (NoSuchMethodException e) {
                //silently ignore
            }
        }
        return false;
    }

    private List<Class<?>> getBridgeTypes(Class<?> mostSpecificType) {
        List<Class<?>> result = new ArrayList<>();
        Class<?> current = mostSpecificType;
        while (current.getName().startsWith("co.elastic.apm")) {
            result.add(current);
            current = current.getSuperclass();
        }
        return result;
    }


    private List<Class<?>> getAllOtelSuperInterfaces(Class<?> type) {
        List<Class<?>> results = new ArrayList<>();
        Class<?> currentType = type;
        while (currentType != null) {
            getAllOtelSuperInterfaces(currentType, results);
            currentType = currentType.getSuperclass();
        }
        return results;
    }

    private void getAllOtelSuperInterfaces(Class<?> type, List<Class<?>> results) {
        for (Class<?> interf : type.getInterfaces()) {
            if (interf.getName().startsWith("io.opentelemetry.")) {
                results.add(interf);
            }
            getAllOtelSuperInterfaces(interf, results);
        }
    }
}
