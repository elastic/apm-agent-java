/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.impl.stacktrace;

import co.elastic.apm.objectpool.NoopObjectPool;
import co.elastic.apm.objectpool.ObjectPool;
import co.elastic.apm.objectpool.RecyclableObjectFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public interface StacktraceFactory {
    void fillStackTrace(List<Stacktrace> stacktrace);

    boolean isAvailable();

    void fillStackTrace(List<Stacktrace> stacktrace, StackTraceElement[] stackTrace);

    enum Noop implements StacktraceFactory {

        INSTANCE;

        @Override
        public void fillStackTrace(List<Stacktrace> stacktrace) {
            // noop
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void fillStackTrace(List<Stacktrace> stacktrace, StackTraceElement[] stackTrace) {
            // noop
        }
    }

    /*
     * Thread.currentThread().getStackTrace()
     * This serves as the base line
     *
     * sun.misc.JavaLangAccess#getStackTraceElement
     * is ~10% faster and requires ~20% less allocated bytes per operation (when reusing Stacktrace objects)
     * but it's an internal API and is not available in Java 9+ so when using it, the code can't be compiled with Java 9
     *
     * StackWalker
     * Java 9's StackWalker has about the same execution time but allocates twice as much memory per operation.
     */
    class CurrentThreadStackTraceFactory implements StacktraceFactory {

        private final StacktraceConfiguration stacktraceConfiguration;
        private final Collection<String> excludedStackFrames = Arrays.asList("java.lang.reflect", "com.sun", "sun.", "jdk.internal.");
        private final ObjectPool<Stacktrace> stacktraceObjectPool;

        public CurrentThreadStackTraceFactory(StacktraceConfiguration stacktraceConfiguration) {
            this(stacktraceConfiguration, new NoopObjectPool<>(new RecyclableObjectFactory<Stacktrace>() {
                @Override
                public Stacktrace createInstance() {
                    return new Stacktrace();
                }
            }));
        }

        public CurrentThreadStackTraceFactory(StacktraceConfiguration stacktraceConfiguration, ObjectPool<Stacktrace> stacktraceObjectPool) {
            this.stacktraceConfiguration = stacktraceConfiguration;
            this.stacktraceObjectPool = stacktraceObjectPool;
        }

        @Override
        public void fillStackTrace(List<Stacktrace> stacktrace) {
            fillStackTrace(stacktrace, Thread.currentThread().getStackTrace());
        }

        @Override
        public void fillStackTrace(List<Stacktrace> stacktrace, StackTraceElement[] stackTrace) {
            boolean topMostElasticApmPackagesSkipped = false;

            int collectedStackFrames = 0;
            int stackTraceLimit = stacktraceConfiguration.getStackTraceLimit();
            for (int i = 1; i < stackTrace.length && collectedStackFrames < stackTraceLimit; i++) {
                StackTraceElement stackTraceElement = stackTrace[i];
                if (!topMostElasticApmPackagesSkipped && stackTraceElement.getClassName().startsWith("co.elastic.apm")) {
                    continue;
                }
                topMostElasticApmPackagesSkipped = true;

                if (isExcluded(stackTraceElement)) {
                    continue;
                }

                stacktrace.add(getStacktrace(stackTraceElement));
                collectedStackFrames++;
            }
        }

        private Stacktrace getStacktrace(StackTraceElement stackTraceElement) {
            Stacktrace s = stacktraceObjectPool.createInstance()
                .withAbsPath(stackTraceElement.getClassName())
                .withFilename(stackTraceElement.getFileName())
                .withFunction(stackTraceElement.getMethodName())
                .withLineno(stackTraceElement.getLineNumber())
                .withLibraryFrame(true);
            for (String applicationPackage : stacktraceConfiguration.getApplicationPackages()) {
                if (stackTraceElement.getClassName().startsWith(applicationPackage)) {
                    s.withLibraryFrame(false);
                }
            }
            return s;
        }

        private boolean isExcluded(StackTraceElement stackTraceElement) {
            // file name is a required field
            if (stackTraceElement.getFileName() == null) {
                return true;
            }
            String className = stackTraceElement.getClassName();
            for (String excludedStackFrame : excludedStackFrames) {
                if (className.startsWith(excludedStackFrame)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }

}
