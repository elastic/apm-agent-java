package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;

public abstract class BaseInstrumentation extends TracerAwareInstrumentation {

    static {
        if (Boolean.parseBoolean(System.getProperty("intellij.debug.agent"))) {
            // InteliJ debugger also instrument some java.util.concurrent classes and changes the class structure.
            // However, the changes are not re-applied when re-transforming already loaded classes, which makes our
            // agent unable to see those structural changes and try to load classes with their original bytecode
            //
            // Go to the following to enable/disable: File | Settings | Build, Execution, Deployment | Debugger | Async Stack Traces
            throw new IllegalStateException("InteliJ debug agent detected, disable it to prevent unexpected instrumentation errors. See https://github.com/elastic/apm-agent-java/issues/1673");
        }
    }
}
