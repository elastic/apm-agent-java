package java.lang;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

public class IndyBootstrap {
    public static Method bootstrap;

    public static CallSite bootstrap(MethodHandles.Lookup lookup,
                                     String adviceMethodName,
                                     MethodType adviceMethodType,
                                     String adviceClassName,
                                     Class<?> instrumentedType,
                                     MethodHandle instrumentedMethod,
                                     String instrumentedMethodName,
                                     int enter) {
        CallSite callSite = null;
        if (bootstrap != null) {
            try {
                callSite = (CallSite) bootstrap.invoke(null, lookup,
                    adviceMethodName,
                    adviceMethodType,
                    adviceClassName,
                    instrumentedType,
                    instrumentedMethod,
                    instrumentedMethodName, enter);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (callSite == null) {
            Class<?> returnType = adviceMethodType.returnType();
            Object returnTypeDefaultValue;
            returnTypeDefaultValue = Array.get(Array.newInstance(returnType, 1), 0);
            MethodHandle noop = MethodHandles.dropArguments(MethodHandles.constant(returnType, returnTypeDefaultValue), 0, adviceMethodType.parameterList());
            callSite = new ConstantCallSite(noop);
        }
        return callSite;
    }
}
