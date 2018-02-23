package co.elastic.apm.impl.stacktrace;

import co.elastic.apm.impl.Stacktrace;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface StacktraceFactory {
    void fillStackTrace(List<Stacktrace> stacktrace);

    boolean isAvailable();

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
    }

    class CurrentThreadStackTraceFactory implements StacktraceFactory {

        private final StacktraceConfiguration stacktraceConfiguration;
        private final Collection<String> excludedStackFrames = Arrays.asList("java.lang.reflect", "com.sun", "sun.", "jdk.internal.");

        public CurrentThreadStackTraceFactory(StacktraceConfiguration stacktraceConfiguration) {
            this.stacktraceConfiguration = stacktraceConfiguration;
        }

        @Override
        public void fillStackTrace(List<Stacktrace> stacktrace) {
            boolean topMostElasticApmPackagesSkipped = false;
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

            int collectedStackFrames = 0;
            int stackTraceLimit = stacktraceConfiguration.getStackTraceLimit();
            for (int i = 1; i <stackTrace.length && collectedStackFrames < stackTraceLimit; i++) {
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
            // TODO no allocation
            Stacktrace s = new Stacktrace()
                .withAbsPath(stackTraceElement.getClassName())
                .withFilename(stackTraceElement.getFileName())
                .withFunction(stackTraceElement.getMethodName())
                .withLineno(stackTraceElement.getLineNumber())
                .withLibraryFrame(true);
            for (String applicationPackage : stacktraceConfiguration.getApplicationPackages()) {
                if (stackTraceElement.getClassName().startsWith(applicationPackage)) {
                    s.setLibraryFrame(false);
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

    // consider multi release jars for this
    @IgnoreJRERequirement
    class StackWalkerStackTraceFactory implements StacktraceFactory {

        private final StacktraceConfiguration stacktraceConfiguration;

        public StackWalkerStackTraceFactory(StacktraceConfiguration stacktraceConfiguration) {
            this.stacktraceConfiguration = stacktraceConfiguration;
        }

        @Override
        public void fillStackTrace(List<Stacktrace> stacktrace) {
            stacktrace.addAll(StackWalker.getInstance()
                .walk(new StreamListFunction()));
        }

        @Override
        public boolean isAvailable() {
            try {
                Class.forName("java.lang.StackWalker");
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        @IgnoreJRERequirement
        private static class StackFramePredicate implements Predicate<StackWalker.StackFrame> {

            private final static StackFramePredicate INSTANCE = new StackFramePredicate();

            @Override
            public boolean test(StackWalker.StackFrame f) {
                return f.getClassName().startsWith("co.elastic.apm");
            }
        }

        @IgnoreJRERequirement
        private class StackFrameStacktraceFunction implements Function<StackWalker.StackFrame, Stacktrace> {
            @Override
            public Stacktrace apply(StackWalker.StackFrame stackFrame) {
                return getStacktrace(stackFrame);
            }

            private Stacktrace getStacktrace(StackWalker.StackFrame stackFrame) {
                Stacktrace st = new Stacktrace()
                    .withAbsPath(stackFrame.getClassName())
                    .withFilename(stackFrame.getFileName())
                    .withFunction(stackFrame.getMethodName())
                    .withLineno(stackFrame.getLineNumber())
                    .withLibraryFrame(true);
                for (String applicationPackage : stacktraceConfiguration.getApplicationPackages()) {
                    if (stackFrame.getClassName().startsWith(applicationPackage)) {
                        st.setLibraryFrame(false);
                    }
                }
                return st;
            }
        }

        @IgnoreJRERequirement
        private class StreamListFunction implements Function<Stream<StackWalker.StackFrame>, List<Stacktrace>> {
            @Override
            public List<Stacktrace> apply(Stream<StackWalker.StackFrame> s) {
                return s
                    .dropWhile(StackFramePredicate.INSTANCE)
                    .limit(stacktraceConfiguration.getStackTraceLimit())
                    .map(new StackFrameStacktraceFunction())
                    .collect(Collectors.<Stacktrace>toList());
            }
        }
    }
}
