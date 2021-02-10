/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.attach;

import co.elastic.logging.log4j2.EcsLayout;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
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
public class RemoteAttacher {

    private final Logger logger;
    private final Arguments arguments;
    private Set<JvmInfo> alreadySeen = new HashSet<>();
    private final Users users = Users.empty();

    private RemoteAttacher(Logger logger, Arguments arguments) {
        this.logger = logger;
        this.arguments = arguments;
        // in case emulated attach is disabled, we need to init provider first, otherwise it's enabled by default
        ElasticAttachmentProvider.init(arguments.useEmulatedAttach());
    }

    private static Logger initLogging(Level logLevel) {
        PluginManager.addPackage(EcsLayout.class.getPackage().getName());
        PluginManager.addPackage(LoggerContext.class.getPackage().getName());
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.add(builder
            .newAppender("Stdout", "CONSOLE")
            .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
            .add(builder.newLayout("EcsLayout")
                .addAttribute("serviceName", "java-attacher")
                .addAttribute("eventDataset", "java-attacher.log")));
        builder.add(builder.newRootLogger(logLevel)
                .add(builder.newAppenderRef("Stdout")));
        Configurator.initialize(RemoteAttacher.class.getClassLoader(), builder.build());
        return LogManager.getLogger(RemoteAttacher.class);
    }

    public static void main(String[] args) {
        Arguments arguments;
        try {
            arguments = Arguments.parse(args);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            arguments = Arguments.parse("--help");
        }
        Logger logger = initLogging(arguments.logLevel);
        RemoteAttacher attacher = new RemoteAttacher(logger, arguments);
        try {
            attacher.attach();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void attach() throws Exception {
        if (arguments.getDiscoveryRules().containsConditionsRelyingOnJps()) {
            if (!Jps.INSTANCE.isAvailable()) {
                String locations = JvmDiscoverer.JpsFinder.getJpsPaths(System.getProperties(), System.getenv()).toString();
                throw new IllegalStateException("Matching JVMs with --include-cmd or --exclude-cmd requires jps, unable to find it in searched locations: " + locations);
            }
        }

        if (arguments.isHelp()) {
            arguments.printHelp(System.out);
        } else if (arguments.isList()) {
            Collection<JvmInfo> jvmInfos = JvmDiscoverer.ForCurrentVM.INSTANCE.discoverJvms();
            if (jvmInfos.isEmpty()) {
                System.err.println("No JVMs found");
            }
            for (JvmInfo jvm : jvmInfos) {
                System.out.println(jvm);
            }
        } else if (arguments.isContinuous()) {
            while (true) {
                attachToNewJvms(JvmDiscoverer.ForCurrentVM.INSTANCE.discoverJvms(), arguments.getDiscoveryRules());
                Thread.sleep(1000);
            }
        } else {
            attachToNewJvms(JvmDiscoverer.ForCurrentVM.INSTANCE.discoverJvms(), arguments.getDiscoveryRules());
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

    private void attachToNewJvms(Collection<JvmInfo> jvms, DiscoveryRules rules) {
        for (JvmInfo jvmInfo : getNewJvms(jvms)) {
            if (!jvmInfo.pid.equals(ByteBuddyAgent.ProcessProvider.ForCurrentVm.INSTANCE.resolve())) {
                onJvmStart(jvmInfo, rules);
            }
        }
        alreadySeen = new HashSet<>(jvms);
    }

    private Set<JvmInfo> getNewJvms(Collection<JvmInfo> currentlyRunningJvms) {
        final HashSet<JvmInfo> newJvms = new HashSet<>(currentlyRunningJvms);
        newJvms.removeAll(alreadySeen);
        return newJvms;
    }

    private void onJvmStart(JvmInfo jvmInfo, DiscoveryRules discoveryRules) {
        DiscoveryRules.Condition matchingCondition = discoveryRules.anyMatch(jvmInfo.getPid(), Users.empty().get(jvmInfo.user));
        if (matchingCondition != null) {
            try {
                final Map<String, String> agentArgs = getAgentArgs(jvmInfo);
                logger.info("Attaching the Elastic APM agent to {} with arguments {}", jvmInfo, agentArgs);
                logger.info("Matching condition: " + matchingCondition);
                if (attach(jvmInfo, agentArgs)) {
                    logger.info("Done");
                } else {
                    logger.error("Unable to attach to JVM with PID = {}", jvmInfo.pid);
                }
            } catch (Exception e) {
                logger.error("Unable to attach to JVM with PID = {}", jvmInfo.pid, e);
            }
        } else {
            logger.debug("Not attaching the Elastic APM agent to {}, none of the conditions are met {}.",
                jvmInfo, discoveryRules.getConditions());
        }
    }

    private boolean attach(JvmInfo jvmInfo, Map<String, String> agentArgs) throws IOException, InterruptedException {
        Users.User user = users.get(jvmInfo.user);
        if (user == null) {
            return false;
        }
        if (user.isCurrentUser()) {
            ElasticApmAttacher.attach(jvmInfo.pid, agentArgs);
            return true;
        } else if (user.canSwitchToUser()) {
            return attachAsUser(user, agentArgs, jvmInfo.pid);
        } else {
            logger.warn("Cannot attach to {} because the current user ({}) doesn't have the permissions to switch to user {}",
                jvmInfo, Users.getCurrentUserName(), jvmInfo.user);
            return false;
        }
    }

    public static boolean attachAsUser(Users.User user, Map<String, String> agentArgs, String pid) throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        args.add("--include-pid");
        args.add(pid);
        for (Map.Entry<String, String> entry : agentArgs.entrySet()) {
            args.add("--config");
            args.add(entry.getKey() + "=" + entry.getValue());
        }
        Process process = user.runAsUserWithCurrentClassPath(RemoteAttacher.class, args).inheritIO().start();
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
        final Process argsProvider = new ProcessBuilder(arguments.getArgsProvider(), jvmInfo.pid).start();
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
        private final boolean useEmulatedAttach;
        private final Level logLevel;

        private Arguments(DiscoveryRules rules, Map<String, String> config, String argsProvider, boolean help, boolean list, boolean continuous, boolean useEmulatedAttach, Level logLevel) {
            this.rules = rules;
            this.help = help;
            this.list = list;
            this.continuous = continuous;
            this.logLevel = logLevel;
            if (!config.isEmpty() && argsProvider != null) {
                throw new IllegalArgumentException("Providing both --config and --args-provider is illegal");
            }
            this.config = config;
            this.argsProvider = argsProvider;
            this.useEmulatedAttach = useEmulatedAttach;
        }

        static Arguments parse(String... args) {
            DiscoveryRules rules = new DiscoveryRules();
            Map<String, String> config = new LinkedHashMap<>();
            String argsProvider = null;
            boolean help = args.length == 0;
            boolean list = false;
            boolean continuous = false;
            boolean useEmulatedAttach = true;
            String currentArg = "";
            Level logLevel = Level.INFO;
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
                        case "-c":
                        case "--continuous":
                            continuous = true;
                            break;
                        case "-w":
                        case "--without-emulated-attach":
                            useEmulatedAttach = false;
                            break;
                        case "--include-all":
                            rules.includeAll();
                        case "-p":
                        case "--pid":
                        case "--include-pid":
                        case "-a":
                        case "--args":
                        case "-C":
                        case "--config":
                        case "-A":
                        case "--args-provider":
                        case "-e":
                        case "--exclude":
                        case "--exclude-cmd":
                        case "--exclude-user":
                        case "-i":
                        case "--include":
                        case "--include-cmd":
                        case "--include-user":
                        case "-g":
                        case "--log-level":
                            break;
                        default:
                            throw new IllegalArgumentException("Illegal argument: " + arg);
                    }
                } else {
                    switch (currentArg) {
                        case "-e":
                        case "--exclude":
                        case "--exclude-cmd":
                            rules.excludeCommandsMatching(arg);
                            break;
                        case "-i":
                        case "--include":
                        case "--include-cmd":
                            rules.includeCommandsMatching(arg);
                            break;
                        case "--exclude-user":
                            rules.excludeUser(arg);
                            break;
                        case "--include-user":
                            rules.includeUser(arg);
                            break;
                        case "-p":
                        case "--pid":
                        case "--include-pid":
                            rules.includePid(arg);
                            break;
                        case "-a":
                        case "--args":
                            System.err.println("--args is deprecated in favor of --config");
                            for (String conf : arg.split(";")) {
                                config.put(conf.substring(0, conf.indexOf('=')), conf.substring(conf.indexOf('=') + 1));
                            }
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
                            logLevel = Level.getLevel(arg);
                            break;
                        default:
                            throw new IllegalArgumentException("Illegal argument: " + arg);
                    }
                }
            }
            return new Arguments(rules, config, argsProvider, help, list, continuous, useEmulatedAttach, logLevel);
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

        void printHelp(PrintStream out) {
            out.println("SYNOPSIS");
            out.println("    java -jar apm-agent-attach.jar --include-pid <pid> [--args <agent_arguments>] [--without-emulated-attach]");
            out.println("    java -jar apm-agent-attach.jar [--include-cmd <include_pattern>...] [--exclude-cmd <exclude_pattern>...] [--continuous] [--without-emulated-attach]");
            out.println("                                   [--config <key=value>... | --args-provider <args_provider_script>]");
            out.println("    java -jar apm-agent-attach.jar (--list | --help)");
            out.println();
            out.println("DESCRIPTION");
            out.println("    Attaches the Elastic APM Java agent to a JVM with a specific PID or runs continuously and attaches to all running and starting JVMs which match the filters.");
            out.println("    By default, this program will not attach the agent to any JVM unless any condition is true.");
            out.println("    Conditions can be added by using one of the following options: --include-all, --include-cmd, --exclude-cmd, --include-user, --exclude-user, --include-pid");
            out.println("    You have to specify at least one --include-* rule");
            out.println();
            out.println("OPTIONS");
            out.println("    -l, --list");
            out.println("        Lists all running JVMs.");
            out.println();
            out.println("    -c, --continuous");
            out.println("        If provided, this program continuously runs and attaches to all running and starting JVMs which match the --exclude and --include filters.");
            out.println();
            out.println("    --include-all");
            out.println("        Includes all JVMs for attachment.");
            out.println();
            out.println("    --include-user");
            out.println("        Includes all JVMs for attachment that are running under the given operating system user.");
            out.println("        Make sure that the user this program is running under is either the same user or has permissions to switch to this user.");
            out.println();
            out.println("    --exclude-user");
            out.println("        Includes all JVMs for attachment that are running under the given operating system user.");
            out.println();
            out.println("    --include-pid --pid <pid>");
            out.println("        PID of the JVM to attach. If not provided, attaches to all currently running JVMs which match the --exclude and --include filters.");
            out.println();
            out.println("    --exclude-cmd <exclude_pattern>...");
            out.println("        A list of regular expressions of fully qualified main class names or paths to JARs of applications or any JVM system property of the java process the java agent should not be attached to.");
            out.println("        (Matches the output of 'jps -lv')");
            out.println("        Note: this is only available if jps is installed");
            out.println();
            out.println("    --include-cmd <include_pattern>...");
            out.println("        A list of regular expressions of fully qualified main class names or paths to JARs of applications or any JVM system property of the java process the java agent should be attached to.");
            out.println("        (Matches the output of 'jps -lv')");
            out.println("        Note: this is only available if jps is installed");
            out.println();
            out.println("    -a, --args <agent_arguments>");
            out.println("        Deprecated in favor of --config.");
            out.println("        If set, the arguments are used to configure the agent on the attached JVM (agentArguments of agentmain).");
            out.println("        The syntax of the arguments is 'key1=value1;key2=value1,value2'.");
            out.println();
            out.println("    -C --config <key=value>...");
            out.println("        This repeatable option sets one agent configuration option.");
            out.println("        Example: --config server_urls=http://localhost:8200,http://localhost:8201.");
            out.println();
            out.println("    -A, --args-provider <args_provider_script>");
            out.println("        The name of a program which is called when a new JVM starts up.");
            out.println("        The program gets the pid as an argument");
            out.println("        and returns an arg string which is used to configure the agent on the attached JVM (agentArguments of agentmain).");
            out.println("        When returning a non-zero status code from this program, the agent will not be attached to the starting JVM.");
            out.println("        The syntax of the arguments is 'key1=value1;key2=value1,value2'.");
            out.println("        Note: this option can not be used in conjunction with --include-pid and --args.");
            out.println();
            out.println("    -w, --without-emulated-attach");
            out.println("        Disables using emulated attach, might be required for some JRE/JDKs as a workaround");
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

        boolean useEmulatedAttach() {
            return useEmulatedAttach;
        }

        public DiscoveryRules getDiscoveryRules() {
            return rules;
        }
    }

}
