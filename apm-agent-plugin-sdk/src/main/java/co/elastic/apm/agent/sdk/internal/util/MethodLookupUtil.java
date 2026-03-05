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
package co.elastic.apm.agent.sdk.internal.util;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;

public class MethodLookupUtil {

    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

    @Nullable
    public static MethodHandle find(Class<?> clazz, String methodName, Class<?> rtype, Class<?>... atypes) {
        final MethodType type = MethodType.methodType(rtype, atypes);
        try {
            return lookup.findVirtual(clazz, methodName, type);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            return null;
        }
    }

    public static MethodHandle findOneOf(Class<?> clazz, String[] methodNames, Class<?> rtype, Class<?>... atypes) {
        for (String methodName : methodNames) {
            MethodHandle handle = find(clazz, methodName, rtype, atypes);
            if (handle != null) {
                return handle;
            }
        }
        throw new IllegalStateException("Cannot find one of the methods ['"+ Arrays.asList(methodNames)+"'] for class '"+clazz.getName()+"'!");
    }
}

