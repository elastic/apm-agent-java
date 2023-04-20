package co.elastic.apm.agent.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public abstract class AgentTestContainer<SELF extends GenericContainer<SELF>> extends GenericContainer<SELF> {

    private static final Logger log = LoggerFactory.getLogger(AgentTestContainer.class);

    /**
     * The port that the IDE will listen to, set to IDEA default value
     */
    private static final int DEBUG_PORT = 5005;
    private static final String LOCAL_DEBUG_HOST = "localhost";
    private static final String REMOTE_DEBUG_HOST = "remote-localhost";

    // agent path within container
    private static final String AGENT_JAR_PATH = "/agent.jar";

    private boolean remoteDebug = false;
    private boolean agent = false;
    private Function<SELF, SELF> preStartHook;


    /**
     * Generic container subclass without any customization
     */
    public static class Generic extends AgentTestContainer<Generic> {

        public Generic(String dockerImageName) {
            super(dockerImageName);
        }
    }

    protected AgentTestContainer(String dockerImageName) {
        super(DockerImageName.parse(dockerImageName));
    }

    @Override
    public void start() {
        preStartHook.apply(self());
        try {
            super.start();
        } catch (RuntimeException e) {
            log.error("unable to start container, set breakpoint where this log is generated to debug", e);
        }

        // send container logs to logger for easier debug by default
        followOutput(new Slf4jLogConsumer(log));
    }

    public SELF withJavaAgent() {
        // maybe in the future, we could copy all the agent artifacts at once
        Path agentJar = AgentFileAccessor.getPathToJavaagent();
        this.withCopyFileToContainer(MountableFile.forHostPath(agentJar), AGENT_JAR_PATH);
        agent = true;
        return self();
    }

    /**
     * Pre-start hook that allows to inject JVM configuration like '-javaagent' argument or debugger into the application
     * either directly in the JVM command line, environment variables or application server specific configuration
     *
     * @param preStartHook pre-start hook
     * @return this
     */
    public SELF withPreStartHook(Function<SELF, SELF> preStartHook) {
        this.preStartHook = preStartHook;
        return self();
    }

    public boolean hasJavaAgent() {
        return agent;
    }

    public String getJavaAgentArgument() {
        return "-javaagent:" + AGENT_JAR_PATH;
    }

    public String getJvmArguments() {
        ArrayList<String> opts = new ArrayList<>();
        if (hasRemoteDebug()) {
            opts.add(getRemoteDebugArgument());
        }
        if (hasJavaAgent()) {
            opts.add(getJavaAgentArgument());
        }
        return String.join(" ", opts);
    }

    /**
     * Configures remote debugging automatically for the JVM running in the container.
     * On the IDE side, all is required is to add debugger listening for incoming connections on port 5005
     */
    public SELF withRemoteDebug() {
        boolean isDebugging = false;

        // test if the test code is currently being debugged
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String jvmArg : jvmArgs) {
            if (jvmArg.contains("-agentlib:jdwp=")) {
                isDebugging = true;
            }
        }
        if (!isDebugging) {
            // not debugging
            return self();
        }

        if (!probeDebugger()) {
            log.error("Unable to detect debugger listening on port {}, remote debugging JVM within container will be disabled", DEBUG_PORT);
            return self();
        }

        // make the docker host IP available for remote debug
        // the 'host-gateway' is automatically translated by docker for all OSes
        withExtraHost(REMOTE_DEBUG_HOST, "host-gateway");
        remoteDebug = true;
        return self();
    }

    public boolean hasRemoteDebug() {
        return remoteDebug;
    }

    public String getRemoteDebugArgument() {
        return remoteDebugArgument(REMOTE_DEBUG_HOST);
    }

    private String remoteDebugArgument(String host) {
        return String.format("-agentlib:jdwp=transport=dt_socket,server=n,address=%s:%d,suspend=y", host, DEBUG_PORT);
    }

    private boolean probeDebugger() {
        // the most straightforward way to probe for an active debugger listening on port is to start another JVM
        // with the debug options and check the process exit status. Trying to probe for open network port messes with
        // the debugger and makes IDEA stop it. The only downside of this is that the debugger will first attach to this
        // probe JVM, then the one running in a docker container we are aiming to debug.
        try {
            Process process = new ProcessBuilder()
                .command(JavaExecutable.getBinaryPath().toString(), remoteDebugArgument(LOCAL_DEBUG_HOST), "-version")
                .start();
            process.waitFor(5, TimeUnit.SECONDS);
            return process.exitValue() == 0;
        } catch (InterruptedException | IOException e) {
            return false;
        }
    }
}
