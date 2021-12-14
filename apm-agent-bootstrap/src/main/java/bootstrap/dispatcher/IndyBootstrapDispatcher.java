package bootstrap.dispatcher;
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

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

@SuppressWarnings("unused")
public class IndyBootstrapDispatcher {

    public static Method bootstrap;

    private static final MethodHandle VOID_NOOP;

    static {
        try {
            VOID_NOOP = MethodHandles.publicLookup().findStatic(IndyBootstrapDispatcher.class, "voidNoop", MethodType.methodType(void.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static CallSite bootstrap(MethodHandles.Lookup lookup,
                                     String adviceMethodName,
                                     MethodType adviceMethodType,
                                     Object... args) {
        CallSite callSite = null;
        if (bootstrap != null) {
            try {
                callSite = (CallSite) bootstrap.invoke(null,
                    lookup,
                    adviceMethodName,
                    adviceMethodType,
                    args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (callSite == null) {
            Class<?> returnType = adviceMethodType.returnType();
            MethodHandle noopNoArg;
            if (returnType == void.class) {
                noopNoArg = VOID_NOOP;
            } else if (!returnType.isPrimitive()) {
                noopNoArg = MethodHandles.constant(returnType, null);
            } else {
                noopNoArg = MethodHandles.constant(returnType, Array.get(Array.newInstance(returnType, 1), 0));
            }
            MethodHandle noop = MethodHandles.dropArguments(noopNoArg, 0, adviceMethodType.parameterList());
            callSite = new ConstantCallSite(noop);
        }
        return callSite;
    }

    public static void voidNoop() {
    }
}
