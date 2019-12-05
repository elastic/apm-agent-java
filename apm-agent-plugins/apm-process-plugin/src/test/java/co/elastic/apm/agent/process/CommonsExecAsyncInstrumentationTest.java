package co.elastic.apm.agent.process;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonsExecAsyncInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void asyncProcessWithinTransaction() throws IOException, InterruptedException {
        startTransaction();
        asyncProcessHasTransactionContext(true);
        terminateTransaction();
    }

    @Test
    void asyncProcessOutsideTransaction() throws IOException, InterruptedException {
        asyncProcessHasTransactionContext(false);
    }

    void asyncProcessHasTransactionContext(boolean expectedInTransaction) throws IOException, InterruptedException {
        final AtomicBoolean isRunInTransaction = new AtomicBoolean(false);

        DefaultExecutor executor = new DefaultExecutor() {
            @Override
            protected Thread createThread(final Runnable runnable, String name) {
                Runnable wrapped = new Runnable() {
                    @Override
                    public void run() {
                        // we don't assert directly here as throwing an exception will wait forever
                        isRunInTransaction.set(tracer.getActive() != null);

                        runnable.run();
                    }
                };
                return super.createThread(wrapped, name);
            }
        };

        final AtomicBoolean processProperlyCompleted = new AtomicBoolean(false);

        DefaultExecuteResultHandler handler = new DefaultExecuteResultHandler() {

            // note: calling super is required otherwise process termination is not detected and waits forever

            @Override
            public void onProcessComplete(int exitValue) {
                super.onProcessComplete(exitValue);
                processProperlyCompleted.set(exitValue == 0);
            }

            @Override
            public void onProcessFailed(ExecuteException e) {
                super.onProcessFailed(e);
                processProperlyCompleted.set(false);
            }
        };
        executor.execute(new CommandLine(getJavaBinaryPath()).addArgument("-version"), handler);
        handler.waitFor();

        assertThat(processProperlyCompleted.get())
            .describedAs("async process should have properly executed")
            .isTrue();

        assertThat(isRunInTransaction.get())
            .describedAs("executor runnable %s the expected transaction context", expectedInTransaction ? "in" : "not in")
            .isEqualTo(expectedInTransaction);
    }

    private static String getJavaBinaryPath() {
        boolean isWindows = System.getProperty("os.name").startsWith("Windows");
        String executable = isWindows ? "java.exe" : "java";
        Path path = Paths.get(System.getProperty("java.home"), "bin", executable);
        if (!Files.isExecutable(path)) {
            throw new IllegalStateException("unable to find java path");
        }
        return path.toAbsolutePath().toString();
    }

    private static void startTransaction() {
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, CommonsExecAsyncInstrumentationTest.class.getClassLoader());
        transaction.withType("request").activate();
    }

    private static void terminateTransaction() {
        Transaction transaction = tracer.currentTransaction();
        assertThat(transaction).isNotNull();
        transaction.deactivate().end();
    }
}
