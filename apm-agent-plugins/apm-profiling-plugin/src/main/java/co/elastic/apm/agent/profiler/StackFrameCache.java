package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.impl.transaction.StackFrame;

import java.util.HashMap;
import java.util.Map;

public class StackFrameCache {

    private final Map<String, Map<String, StackFrame>> cache = new HashMap<String, Map<String, StackFrame>>();

    public StackFrame getStackFrame(String className, String methodName) {
        Map<String, StackFrame> methodToFrame = cache.get(className);
        if (methodToFrame == null) {
            methodToFrame = new HashMap<String, StackFrame>();
            cache.put(className, methodToFrame);
        }

        StackFrame stackFrame = methodToFrame.get(methodName);
        if (stackFrame == null) {
            stackFrame = StackFrame.of(className, methodName);
            methodToFrame.put(methodName, stackFrame);
        }

        return stackFrame;
    }
}
