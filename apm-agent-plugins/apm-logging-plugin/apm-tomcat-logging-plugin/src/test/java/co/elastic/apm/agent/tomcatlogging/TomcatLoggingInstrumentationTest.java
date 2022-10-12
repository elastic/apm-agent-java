package co.elastic.apm.agent.tomcatlogging;

import co.elastic.apm.agent.jul.JulInstrumentationTest;
import co.elastic.apm.agent.loginstr.LoggerFacade;
import org.apache.juli.FileHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Handler;
import java.util.logging.SimpleFormatter;

public class TomcatLoggingInstrumentationTest extends JulInstrumentationTest {

    @Override
    protected LoggerFacade createLoggerFacade() {
        // we need to delete previous log files before the logger instance is created
        // as a consequence, we can't rely on Junit @BeforeEach ordering with parent test class
        cleanupPreviousLogFiles();
        return new TomcatLoggerFacade();
    }

    private static void cleanupPreviousLogFiles() {
        Path folder = Paths.get("target", "tomcat");
        String prefix = String.format("catalina.%s", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        try {
            Files.deleteIfExists(folder.resolve(prefix + ".log"));
            Files.deleteIfExists(folder.resolve(prefix + ".ecs.json.0"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class TomcatLoggerFacade extends JulInstrumentationTest.AbstractJulLoggerFacade {

        @Override
        protected void resetRemovedHandler() {
            if (Arrays.stream(julLogger.getHandlers()).noneMatch(handler -> handler instanceof FileHandler)) {
                try {
                    FileHandler fileHandler = new FileHandler("target/tomcat", "catalina.", ".log");
                    fileHandler.setFormatter(new SimpleFormatter());
                    julLogger.addHandler(fileHandler);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public String getLogFilePath() {
            for (Handler loggerHandler : julLogger.getHandlers()) {
                if (loggerHandler instanceof FileHandler) {
                    // no API for that, so we use reflection for tests and the field in the instrumentation

                    try {
                        String directory = getField(loggerHandler, "directory");
                        String prefix = getField(loggerHandler, "prefix");
                        String suffix = getField(loggerHandler, "suffix");
                        String date = getField(loggerHandler, "date");

                        return Paths.get(directory).toAbsolutePath()
                            .resolve(prefix + date + suffix)
                            .toString();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to get log file path through reflection", e);
                    }
                }
            }
            throw new IllegalStateException("Couldn't find a FileHandler for logger " + julLogger.getName());
        }
    }
}
