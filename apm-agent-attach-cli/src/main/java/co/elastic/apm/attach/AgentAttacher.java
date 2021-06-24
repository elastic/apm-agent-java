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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * Attaches the Elastic APM Java agent to a JVM with a specific PID or runs continuously and attaches to all running and starting JVMs which match.
 */
public class AgentAttacher {

    // intentionally not static so that we can initLogging first
    private final Logger logger = LogManager.getLogger(AgentAttacher.class);
    private final Arguments arguments;
    // intentionally not storing JvmInfo as it may hold potentially sensitive information (JVM args)
    // reduces the risk of exposing them in heap dumps
    private final Set<String> alreadySeenJvmPids = new HashSet<>();
    private final UserRegistry userRegistry = UserRegistry.empty();
    private final JvmDiscoverer jvmDiscoverer;

    private AgentAttacher(Arguments arguments) throws Exception {
        this.arguments = arguments;
        ElasticAttachmentProvider.init();
        // fail fast if no attachment provider is working
        GetAgentProperties.getAgentAndSystemProperties(JvmInfo.CURRENT_PID, userRegistry.getCurrentUser());
        this.jvmDiscoverer = new JvmDiscoverer.Compound(Arrays.asList(
            JvmDiscoverer.ForHotSpotVm.withDiscoveredTempDirs(userRegistry),
            new JvmDiscoverer.UsingPs(userRegistry)));
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
            if (arguments.getAgentJar() == null) {
                throw new IllegalArgumentException("When using the slim jar, the --agent-jar argument is required");
            }
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            Arguments.printHelp(System.out);
            return;
        }
        Logger logger = initLogging(arguments);

        if (logger.isDebugEnabled()) {
            logger.debug("attacher arguments : {}", arguments);
        }
        try {
            new AgentAttacher(arguments).handleNewJvmsLoop();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void handleNewJvmsLoop() throws Exception {
        while (true) {
            handleNewJvms(jvmDiscoverer.discoverJvms(), arguments.getDiscoveryRules());
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
                logger.debug("Include rule {} matches for JVM {}", firstMatch, jvmInfo);
                onJvmMatch(jvmInfo);
            } else {
                logger.debug("Exclude rule {} matches for JVM {}", firstMatch, jvmInfo);
            }
        } else {
            logger.debug("No rule matches for JVM, thus excluding {}", jvmInfo);
        }
    }

    private void onJvmMatch(JvmInfo jvmInfo) throws Exception {
        final Map<String, String> agentArgs = getAgentArgs(jvmInfo);
        if (arguments.isList()) {
            System.out.println(jvmInfo.toString(arguments.isListVmArgs()));
        } else {
            logger.info("Attaching the Elastic APM agent to {} with arguments {}", jvmInfo, agentArgs);
            if (attach(jvmInfo, agentArgs)) {
                logger.info("Done");
            } else {
                logger.error("Unable to attach to JVM with PID = {}", jvmInfo.getPid());
            }
        }
    }

    private boolean attach(JvmInfo jvmInfo, Map<String, String> agentArgs) throws Exception {
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

    private boolean attachAsUser(UserRegistry.User user, Map<String, String> agentArgs, String pid) throws IOException, InterruptedException {

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
        Process process = user.runAsUserWithCurrentClassPath(AgentAttacher.class, args).inheritIO().start();
        process.waitFor();
        return process.exitValue() == 0;
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
        private final Map<String, String> config;
        private final String argsProvider;
        private final boolean help;
        private final boolean list;
        private final boolean continuous;
        private final Level logLevel;
        private final String logFile;
        private final boolean listVmArgs;
        private final File agentJar;

        private Arguments(DiscoveryRules rules, Map<String, String> config, String argsProvider, boolean help, boolean list, boolean listVmArgs, boolean continuous, Level logLevel, String logFile, String agentJarString) {
            this.rules = rules;
            this.help = help;
            this.list = list;
            this.listVmArgs = listVmArgs;
            this.continuous = continuous;
            this.logLevel = logLevel;
            this.logFile = logFile;
            if (agentJarString != null) {
                agentJar = new File(agentJarString);
                if (!agentJar.exists()) {
                    throw new IllegalArgumentException(String.format("Agent jar %s does not exist", agentJarString));
                }
                if (!agentJar.canRead()) {
                    throw new IllegalArgumentException(String.format("Agent jar %s is not readable", agentJarString));
                }
            } else {
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
            Map<String, String> config = new LinkedHashMap<>();
            String argsProvider = null;
            boolean help = args.length == 0;
            boolean list = false;
            boolean listVmArgs = false;
            boolean continuous = false;
            String currentArg = "";
            Level logLevel = Level.INFO;
            String logFile = null;
            String agentJar = null;
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
                        case "--include-vmargs":
                        case "--exclude-vmargs":
                        case "-g":
                        case "--log-level":
                        case "--log-file":
                        case "--agent-jar":
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
                        case "--include-vmargs":
                            rules.includeVmArgs(arg);
                            break;
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
                            rules.includePid(arg);
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
                        default:
                            throw new IllegalArgumentException("Illegal argument: " + arg);
                    }
                }
            }
            return new Arguments(rules, config, argsProvider, help, list, listVmArgs, continuous, logLevel, logFile, agentJar);
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
            out.println("    --include-all");
            out.println("        Includes all JVMs for attachment.");
            out.println();
            out.println("    --include-pid <pid>...");
            out.println("        A list of PIDs to include.");
            out.println();
            out.println("    --include-main/--exclude-main <pattern>...");
            out.println("        A list of regular expressions of fully qualified main class names or paths to JARs of applications the java agent should be attached to.");
            out.println("        Performs a partial match so that `foo` matches `/bin/foo.jar`.");
            out.println();
            out.println("    --include-vmarg/--exclude-vmarg <pattern>...");
            out.println("        A list of regular expressions matched against the arguments passed to the JVM, such as system properties.");
            out.println("        Performs a partial match so that `attach=true` matches the system property `-Dattach=true`.");
            out.println();
            out.println("    --include-user/--exclude-user <user>...");
            out.println("        A list of usernames that are matched against the operating system user that run the JVM.");
            out.println("        For included users, make sure that the user this program is running under is either the same user or has permissions to switch to the user that runs the target JVM.");
            out.println();
            out.println("    -C --config <key=value>...");
            out.println("        This repeatable option sets one agent configuration option.");
            out.println("        Example: --config server_url=http://localhost:8200.");
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

        public DiscoveryRules getDiscoveryRules() {
            return rules;
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
