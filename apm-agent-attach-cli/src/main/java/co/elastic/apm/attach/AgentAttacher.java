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
package co.elastic.apm.attach;

import co.elastic.apm.agent.common.util.ProcessExecutionUtil;
import co.elastic.logging.log4j2.EcsLayout;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;

/**
 * Attaches the Elastic APM Java agent to a JVM with a specific PID or runs continuously and attaches to all running and starting JVMs which match.
 */
public class AgentAttacher {

    public static final String LATEST_VERSION = "latest";
    // intentionally not static so that we can initLogging first
    private final Logger logger = LogManager.getLogger(AgentAttacher.class);
    private final Arguments arguments;
    // intentionally not storing JvmInfo as it may hold potentially sensitive information (JVM args)
    // reduces the risk of exposing them in heap dumps
    private final Set<String> alreadySeenJvmPids = new HashSet<>();
    private final UserRegistry userRegistry = UserRegistry.empty();

    private AgentAttacher(Arguments arguments) {
        this.arguments = arguments;
    }

    private static Logger initLogging(Arguments arguments) {
        PluginManager.addPackage(EcsLayout.class.getPackage().getName());
        PluginManager.addPackage(LoggerContext.class.getPackage().getName());
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();

        if (arguments.isLogToConsole()) {
            builder.add(builder
                .newAppender("Stdout", "CONSOLE")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                .add(getEcsLayout(builder)));
            builder.add(builder.newRootLogger(arguments.logLevel)
                .add(builder.newAppenderRef("Stdout")));
        } else {
            builder.add(builder.newAppender("File", "RollingFile")
                .addAttribute("fileName", arguments.getLogFile())
                .addAttribute("filePattern", arguments.getLogFile() + ".%i")
                .add(getEcsLayout(builder))
                .addComponent(builder.newComponent("Policies")
                    .addComponent(builder.newComponent("SizeBasedTriggeringPolicy").addAttribute("size", "10MB")))
                .addComponent(builder.newComponent("DefaultRolloverStrategy").addAttribute("max", 2)));
            builder.add(builder.newRootLogger(arguments.logLevel)
                .add(builder.newAppenderRef("File")));
        }

        Configurator.initialize(AgentAttacher.class.getClassLoader(), builder.build());
        return LogManager.getLogger(AgentAttacher.class);
    }

    private static LayoutComponentBuilder getEcsLayout(ConfigurationBuilder<BuiltConfiguration> builder) {
        return builder.newLayout("EcsLayout")
            .addAttribute("serviceName", "java-attacher")
            .addAttribute("eventDataset", "java-attacher.log");
    }

    public static void main(String[] args) {
        Arguments arguments;
        try {
            arguments = Arguments.parse(args);
            if (arguments.isHelp()) {
                Arguments.printHelp(System.out);
                return;
            }
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            Arguments.printHelp(System.out);
            return;
        }
        Logger logger = initLogging(arguments);

        if (logger.isDebugEnabled()) {
            logger.debug("attach process started with:  user = '{}', current directory = {}", System.getProperty("user.name"), System.getProperty("user.dir"));
        }

        String downloadAgentVersion = arguments.getDownloadAgentVersion();
        if (downloadAgentVersion == null && arguments.getAgentJar() == null) {
            // If there is no bundled agent and a path for agent was not specified, default to download the latest
            downloadAgentVersion = LATEST_VERSION;
        }
        if (downloadAgentVersion != null) {
            try {
                downloadAndVerifyAgent(arguments, downloadAgentVersion);
            } catch (Exception e) {
                logger.error(String.format("Failed to download requested agent version %s, please double-check your " +
                    "--download-agent-version setting.", downloadAgentVersion), e);
                System.exit(1);
            }
        }

        if (arguments.getAgentJar() == null) {
            logger.error("Cannot find agent jar. When using the slim jar, either the --agent-jar or the " +
                "--download-agent-version arguments are required");
            System.exit(1);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("attacher arguments : {}", arguments);
        }
        try {
            new AgentAttacher(arguments).doAttach();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static void downloadAndVerifyAgent(Arguments arguments, String downloadAgentVersion) throws Exception {
        if (downloadAgentVersion.equalsIgnoreCase(LATEST_VERSION)) {
            downloadAgentVersion = AgentDownloader.findLatestVersion();
        }
        PgpSignatureVerifier pgpSignatureVerifier;
        try {
            Path targetLibDir = AgentDownloadUtils.of(downloadAgentVersion).getTargetLibDir();
            PgpSignatureVerifierLoader verifierLoader = PgpSignatureVerifierLoader.getInstance(
                "/bc-lib",
                targetLibDir,
                "co.elastic.apm.attach.bouncycastle.BouncyCastleVerifier"
            );
            pgpSignatureVerifier = verifierLoader.loadPgpSignatureVerifier();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load PGP signature verifier implementation", e);
        }

        Path downloadedJarPath = new AgentDownloader(pgpSignatureVerifier).downloadAndVerifyAgent(downloadAgentVersion);
        if (!Files.isReadable(downloadedJarPath)) {
            throw new IllegalStateException(String.format("Cannot read agent jar at %s", downloadedJarPath));
        }
        arguments.setAgentJar(downloadedJarPath.toFile());
    }

    private void doAttach() {
        try {
            DiscoveryRules discoveryRules = arguments.getDiscoveryRules();
            if (!discoveryRules.isDiscoveryRequired() && arguments.isNoFork()) {
                attachToSpecificPidsAsCurrentUser();
            } else {
                discoverAndAttachLoop(discoveryRules);
            }
        } catch (Exception e) {
            logger.error("Error during attachment", e);
        }
    }

    private void attachToSpecificPidsAsCurrentUser() throws Exception {
        // a shortcut for a simple usage of attaching to specific PIDs using the current user, which means we can avoid all
        // JVM discovery logic and user-switches
        Set<String> includePids = arguments.getIncludePids();
        for (String includePid : includePids) {
            Properties properties = GetAgentProperties.getAgentAndSystemProperties(includePid, userRegistry.getCurrentUser());
            attach(JvmInfo.withCurrentUser(includePid, properties));
        }
    }

    private void discoverAndAttachLoop(final DiscoveryRules discoveryRules) throws Exception {
        // fail fast if no attachment provider is working
        GetAgentProperties.getAgentAndSystemProperties(JvmInfo.CURRENT_PID, userRegistry.getCurrentUser());

        JvmDiscoverer jvmDiscoverer = new JvmDiscoverer.Compound(Arrays.asList(
            JvmDiscoverer.ForHotSpotVm.withDiscoveredTempDirs(userRegistry),
            new JvmDiscoverer.UsingPs(userRegistry))
        );

        while (true) {
            handleNewJvms(jvmDiscoverer.discoverJvms(), discoveryRules);
            if (!arguments.isContinuous()) {
                break;
            }
            Thread.sleep(1000);
        }
    }

    static String toString(InputStream inputStream) throws IOException {
        try {
            Scanner scanner = new Scanner(inputStream, "UTF-8").useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        } finally {
            inputStream.close();
        }
    }

    private void handleNewJvms(Collection<JvmInfo> jvms, DiscoveryRules rules) {
        for (JvmInfo jvmInfo : jvms) {
            if (alreadySeenJvmPids.contains(jvmInfo.getPid())) {
                continue;
            }
            alreadySeenJvmPids.add(jvmInfo.getPid());
            if (!jvmInfo.isCurrentVM()) {
                try {
                    onJvmStart(jvmInfo, rules);
                } catch (Exception e) {
                    logger.error("Unable to attach to JVM with PID = {}", jvmInfo.getPid(), e);
                }
            }
        }
    }

    private void onJvmStart(JvmInfo jvmInfo, DiscoveryRules discoveryRules) throws Exception {
        DiscoveryRules.DiscoveryRule firstMatch = discoveryRules.firstMatch(jvmInfo, userRegistry);
        if (firstMatch != null) {
            if (firstMatch.getMatchingType() == DiscoveryRules.MatcherType.INCLUDE) {
                logger.info("Include rule {} matches for JVM {}", firstMatch, jvmInfo);
                onJvmMatch(jvmInfo);
            } else {
                logger.info("Exclude rule {} matches for JVM {}", firstMatch, jvmInfo);
            }
        } else {
            logger.info("No rule matches for JVM, thus excluding {}", jvmInfo);
        }
    }

    private void onJvmMatch(JvmInfo jvmInfo) throws Exception {
        if (arguments.isList()) {
            System.out.println(jvmInfo.toString(arguments.isListVmArgs()));
        } else {
            if (attach(jvmInfo)) {
                logger.info("Done");
            } else {
                logger.error("Unable to attach to JVM with PID = {}", jvmInfo.getPid());
            }
        }
    }

    private boolean attach(JvmInfo jvmInfo) throws Exception {
        final Map<String, String> agentArgs = getAgentArgs(jvmInfo);
        if (!agentArgs.containsKey("activation_method")) {
            agentArgs.put("activation_method", "APM_AGENT_ATTACH_CLI");
        }
        logger.info("Attaching the Elastic APM agent to {} with arguments {}", jvmInfo, agentArgs);

        UserRegistry.User user = jvmInfo.getUser(userRegistry);
        if (user == null) {
            logger.error("Could not load user {}", jvmInfo.getUserName());
            return false;
        }
        if (!jvmInfo.isVersionSupported()) {
            logger.info("Cannot attach to JVM {} as the version {} is not supported.", jvmInfo, jvmInfo.getJavaVersion());
            return false;
        }
        if (jvmInfo.isAlreadyAttached()) {
            logger.info("The agent is already attached to JVM {}", jvmInfo);
            return false;
        }
        if (user.isCurrentUser()) {
            ElasticApmAttacher.attach(jvmInfo.getPid(), agentArgs, arguments.getAgentJar());
            return true;
        } else if (user.canSwitchToUser()) {
            return attachAsUser(user, agentArgs, jvmInfo.getPid());
        } else {
            logger.warn("Cannot attach to {} because the current user ({}) doesn't have the permissions to switch to user {}",
                jvmInfo, UserRegistry.getCurrentUserName(), jvmInfo.getUserName());
            return false;
        }
    }

    private boolean attachAsUser(UserRegistry.User user, Map<String, String> agentArgs, String pid) {

        List<String> args = new ArrayList<>();
        args.add("--include-pid");
        args.add(pid);
        for (Map.Entry<String, String> entry : agentArgs.entrySet()) {
            args.add("--config");
            args.add(entry.getKey() + "=" + entry.getValue());
        }
        if (arguments.getLogLevel() != null) {
            args.add("--log-level");
            args.add(arguments.getLogLevel().toString());
        }
        if (arguments.getLogFile() != null) {
            args.add("--log-file");
            args.add(arguments.getLogFile());
        }
        ProcessExecutionUtil.CommandOutput output = user.executeAsUserWithCurrentClassPath(AgentAttacher.class, args);
        return output.exitedNormally();
    }

    private Map<String, String> getAgentArgs(JvmInfo jvmInfo) throws IOException, InterruptedException {
        if (arguments.getArgsProvider() != null) {
            LinkedHashMap<String, String> config = new LinkedHashMap<>();
            for (String conf : getArgsProviderOutput(jvmInfo).split(";")) {
                config.put(conf.substring(0, conf.indexOf('=')), conf.substring(conf.indexOf('=') + 1));
            }
            return config;
        } else {
            return arguments.getConfig();
        }
    }

    private String getArgsProviderOutput(JvmInfo jvmInfo) throws IOException, InterruptedException {
        final Process argsProvider = new ProcessBuilder(arguments.getArgsProvider(), jvmInfo.getPid()).start();
        if (argsProvider.waitFor() == 0) {
            return toString(argsProvider.getInputStream());
        } else {
            logger.info("Not attaching the Elastic APM agent to {}, " +
                "because the '--args-provider {}' script ended with a non-zero status code.", jvmInfo, arguments.argsProvider);
            throw new IllegalStateException(toString(argsProvider.getErrorStream()));
        }
    }

    static class Arguments {
        private final DiscoveryRules rules;
        private final Set<String> includePids;
        private final Map<String, String> config;
        private final String argsProvider;
        private final boolean help;
        private final boolean list;
        private final boolean continuous;
        private final boolean noFork;
        private final Level logLevel;
        private final String logFile;
        private final boolean listVmArgs;
        private final String downloadAgentVersion;
        private File agentJar;

        private Arguments(DiscoveryRules rules, Set<String> includePids, Map<String, String> config, String argsProvider, boolean help,
                          boolean list, boolean listVmArgs, boolean continuous, boolean noFork, Level logLevel, String logFile,
                          String agentJarString, String downloadAgentVersion) {
            this.rules = rules;
            this.includePids = includePids;
            this.help = help;
            this.list = list;
            this.listVmArgs = listVmArgs;
            this.continuous = continuous;
            this.noFork = noFork;
            this.logLevel = logLevel;
            this.logFile = logFile;
            this.downloadAgentVersion = downloadAgentVersion;
            if (agentJarString != null) {
                agentJar = new File(agentJarString);
                if (!agentJar.exists()) {
                    throw new IllegalArgumentException(String.format("Agent jar %s does not exist", agentJarString));
                }
                if (!agentJar.canRead()) {
                    throw new IllegalArgumentException(String.format("Agent jar %s is not readable", agentJarString));
                }
            } else if (downloadAgentVersion == null) {
                // this would fail if using the slim attacher CLI jar without providing either --agent-jar or --download-agent-version
                this.agentJar = ElasticApmAttacher.getBundledAgentJarFile();
            }
            if (!config.isEmpty() && argsProvider != null) {
                throw new IllegalArgumentException("Providing both --config and --args-provider is illegal");
            }
            this.config = config;
            this.argsProvider = argsProvider;
        }

        static Arguments parse(String... args) {
            DiscoveryRules rules = new DiscoveryRules();
            Set<String> includePids = new HashSet<>();
            Map<String, String> config = new LinkedHashMap<>();
            String argsProvider = null;
            boolean help = args.length == 0;
            boolean list = false;
            boolean listVmArgs = false;
            boolean continuous = false;
            boolean noFork = false;
            String currentArg = "";
            Level logLevel = Level.INFO;
            String logFile = null;
            String agentJar = null;
            String downloadedAgentVersion = null;
            for (String arg : normalize(args)) {
                if (arg.startsWith("-")) {
                    currentArg = arg;
                    switch (arg) {
                        case "-h":
                        case "--help":
                            help = true;
                            break;
                        case "-l":
                        case "--list":
                            list = true;
                            break;
                        case "-v":
                        case "--list-vmargs":
                            listVmArgs = true;
                            break;
                        case "-c":
                        case "--continuous":
                            continuous = true;
                            break;
                        case "--no-fork":
                            noFork = true;
                            break;
                        case "--include-all":
                            rules.includeAll();
                        case "-C":
                        case "--config":
                        case "-A":
                        case "--args-provider":
                        case "--include-pid":
                        case "--include-main":
                        case "--exclude-main":
                        case "--include-user":
                        case "--exclude-user":
                        case "--include-vmarg":
                        case "--include-vmargs":
                        case "--exclude-vmarg":
                        case "--exclude-vmargs":
                        case "-g":
                        case "--log-level":
                        case "--log-file":
                        case "--agent-jar":
                        case "--download-agent-version":
                            break;
                        default:
                            throw new IllegalArgumentException("Illegal argument: " + arg);
                    }
                } else {
                    switch (currentArg) {
                        case "--include-main":
                            rules.includeMain(arg);
                            break;
                        case "--exclude-main":
                            rules.excludeMain(arg);
                            break;
                        case "--include-vmarg":
                        case "--include-vmargs":
                            rules.includeVmArgs(arg);
                            break;
                        case "--exclude-vmarg":
                        case "--exclude-vmargs":
                            rules.excludeVmArgs(arg);
                            break;
                        case "--include-user":
                            rules.includeUser(arg);
                            break;
                        case "--exclude-user":
                            rules.excludeUser(arg);
                            break;
                        case "--include-pid":
                            // "include-pid" rules do not require discovery, however we add them to the discovery rules because
                            // theoretically they may be used AFTER other exclusion rules, in which case we need to make sure we only
                            // match against them in the correct order
                            rules.includePid(arg);
                            includePids.add(Objects.requireNonNull(arg));
                            break;
                        case "-C":
                        case "--config":
                            config.put(arg.substring(0, arg.indexOf('=')), arg.substring(arg.indexOf('=') + 1));
                            break;
                        case "-A":
                        case "--args-provider":
                            argsProvider = arg;
                            break;
                        case "-g":
                        case "--log-level":
                            logLevel = Level.valueOf(arg);
                            break;
                        case "--log-file":
                            logFile = arg;
                            break;
                        case "--agent-jar":
                            agentJar = arg;
                            break;
                        case "--download-agent-version":
                            downloadedAgentVersion = arg;
                            break;
                        default:
                            throw new IllegalArgumentException("Illegal argument: " + arg);
                    }
                }
            }
            return new Arguments(rules, includePids, config, argsProvider, help, list, listVmArgs, continuous, noFork, logLevel, logFile,
                agentJar, downloadedAgentVersion);
        }

        // -ab -> -a -b
        private static List<String> normalize(String[] args) {
            final List<String> normalizedArgs = new ArrayList<>(args.length);
            for (String arg : args) {
                if (arg.startsWith("-") && !arg.startsWith("--")) {
                    for (int i = 1; i < arg.length(); i++) {
                        normalizedArgs.add("-" + arg.charAt(i));
                    }
                } else {
                    normalizedArgs.add(arg);
                }
            }
            return normalizedArgs;
        }

        static void printHelp(PrintStream out) {
            out.println("SYNOPSIS");
            out.println("    java -jar apm-agent-attach-cli.jar [--include-* <pattern>...] [--exclude-* <pattern>...]");
            out.println("                                       [--continuous]");
            out.println("                                       [--config <key=value>... | --args-provider <args_provider_script>]");
            out.println("                                       [--list] [--list-vmargs]");
            out.println("                                       [--log-level <level>]");
            out.println("    java -jar apm-agent-attach-cli.jar --help");
            out.println();
            out.println("DESCRIPTION");
            out.println("    Attaches the Elastic APM Java agent to all running JVMs that match the `--include-*` / `--exclude-*` discovery rules.");
            out.println("    For every running JVM, the discovery rules are evaluated in the order they are provided.");
            out.println("    The first matching rule determines the outcome.");
            out.println("    * If the first matching rules is an exclude, the agent will not be attached.");
            out.println("    * If the first matching rules is an include, the agent will be attached.");
            out.println("    * If no rule matches, the agent will not be attached.");
            out.println();
            out.println("OPTIONS");
            out.println("    -l, --list");
            out.println("        This lets you do a dry run of the include/exclude discovery rules.");
            out.println("        Instead of attaching to matching JVMs, the programm will print JVMs that match the include/exclude discovery rules.");
            out.println("        Similar to `jps -l`, the output includes the PID and the main class name or the path to the jar file.");
            out.println();
            out.println("    -v, --list-vmargs");
            out.println("        When listing running JVMs via `--list`, include the arguments passed to the JVM.");
            out.println("        Provides an output similar to `jps -lv`.");
            out.println("        Note: The JVM arguments may contain sensitive information, such as passwords provided via system properties.");
            out.println();
            out.println("    -c, --continuous");
            out.println("        If provided, this program continuously runs and attaches to all running and starting JVMs which match the --exclude and --include filters.");
            out.println();
            out.println("    --no-fork");
            out.println("        By default, when the attacher program is ran by user A and the target process is ran by user B, ");
            out.println("        the attacher will attempt to start another process as user B. ");
            out.println("        If this configuration option is provided, the attacher will not fork. Instead, it will attempt to attach directly as the current user.");
            out.println();
            out.println("    --include-all");
            out.println("        Includes all JVMs for attachment.");
            out.println();
            out.println("    --include-pid <pid>...");
            out.println("        A list of PIDs to include.");
            out.println();
            out.println("    --include-main/--exclude-main <pattern>");
            out.println("        A regular expression of fully qualified main class names or paths to JARs of applications the java agent should be attached to.");
            out.println("        Performs a partial match so that `foo` matches `/bin/foo.jar`.");
            out.println();
            out.println("    --include-vmarg/--exclude-vmarg <pattern>");
            out.println("        A regular expression that is matched against the arguments passed to the JVM, such as system properties.");
            out.println("        Performs a partial match so that `attach=true` matches the system property `-Dattach=true`.");
            out.println();
            out.println("    --include-user/--exclude-user <user>");
            out.println("        A username that is matched against the operating system user that run the JVM.");
            out.println("        For included users, make sure that the user this program is running under is either the same user or has permissions to switch to the user that runs the target JVM.");
            out.println();
            out.println("    -C --config <key=value>...");
            out.println("        This repeatable option sets one agent configuration option.");
            out.println("        Example: --config server_url=http://127.0.0.1:8200.");
            out.println();
            out.println("    -A, --args-provider <args_provider_script>");
            out.println("        The name of a program which is called when a new JVM starts up.");
            out.println("        The program gets the pid as an argument");
            out.println("        and returns an arg string which is used to configure the agent on the attached JVM (agentArguments of agentmain).");
            out.println("        When returning a non-zero status code from this program, the agent will not be attached to the starting JVM.");
            out.println("        The syntax of the arguments is 'key1=value1;key2=value1,value2'.");
            out.println("        Note: this option can not be used in conjunction with --include-pid and --args.");
            out.println();
            out.println("    -g, --log-level <off|fatal|error|warn|info|debug|trace|all>");
            out.println("        Configures the verbosity of the logs that are sent to stdout with an ECS JSON format.");
            out.println();
            out.println("    --log-file <file>");
            out.println("        To log into a file instead of the console, specify a path to a file that this program should log into.");
            out.println("        The log file rolls over once the file has reached a size of 10MB.");
            out.println("        One history file will be kept with the name `${logFile}.1`.");
            out.println();
            out.println("    --agent-jar <file>");
            out.println("        Instead of the bundled agent jar, attach the provided agent to the target JVMs.");
            out.println();
            out.println("    --download-agent-version <agent-version>");
            out.println("        Instead of the bundled agent jar, download and attach the specified agent version from maven.");
            out.println("        <agent-version> can be either the explicit version (for example: `1.15.0`) or `latest`.");
        }

        Map<String, String> getConfig() {
            return config;
        }

        String getArgsProvider() {
            return argsProvider;
        }

        boolean isHelp() {
            return help;
        }

        boolean isList() {
            return list;
        }

        boolean isContinuous() {
            return continuous;
        }

        boolean isNoFork() {
            return noFork;
        }

        public DiscoveryRules getDiscoveryRules() {
            return rules;
        }

        public Set<String> getIncludePids() {
            return includePids;
        }

        public boolean isListVmArgs() {
            return listVmArgs;
        }

        public String getLogFile() {
            return logFile;
        }

        public boolean isLogToConsole() {
            return logFile == null;
        }

        public String getDownloadAgentVersion() {
            return downloadAgentVersion;
        }

        public void setAgentJar(File agentJar) {
            this.agentJar = agentJar;
        }

        public File getAgentJar() {
            return agentJar;
        }

        public Level getLogLevel() {
            return logLevel;
        }

        @Override
        public String toString() {
            return "Arguments{" +
                "rules=" + rules +
                ", config=" + config +
                ", argsProvider='" + argsProvider + '\'' +
                ", help=" + help +
                ", list=" + list +
                ", continuous=" + continuous +
                ", logLevel=" + logLevel +
                ", logFile='" + logFile + '\'' +
                ", listVmArgs=" + listVmArgs +
                ", agentJar=" + agentJar +
                '}';
        }
    }

}
