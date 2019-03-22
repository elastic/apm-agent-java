/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.attach;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;

/**
 * Attaches the Elastic APM Java agent to a JVM with a specific PID or runs continuously and attaches to all running and starting JVMs which match.
 */
public class RemoteAttacher {

    private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private final Arguments arguments;
    private Set<JvmInfo> runningJvms = new HashSet<>();

    private RemoteAttacher(Arguments arguments) {
        this.arguments = arguments;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Arguments arguments;
        try {
            arguments = Arguments.parse(args);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            arguments = Arguments.parse("--help");
        }
        final RemoteAttacher attacher = new RemoteAttacher(arguments);
        if (arguments.isHelp()) {
            arguments.printHelp(System.out);
        } else if (arguments.isList()) {
            System.out.println(getJpsOutput());
        } else if (arguments.getPid() != null) {
            log("INFO", "Attaching the Elastic APM agent to %s", arguments.getPid());
            ElasticApmAttacher.attach(arguments.getPid(), arguments.getConfig());
            log("INFO", "Done");
        } else {
            do {
                attacher.attachToNewJvms(getJpsOutput());
                Thread.sleep(1000);
            } while (arguments.isContinuous());
        }
    }

    private static void log(String level, String message, Object... args) {
        System.out.println(String.format("%s %5s ", df.format(new Date()), level) + String.format(message, args));
    }

    @Nonnull
    private static String getJpsOutput() throws IOException, InterruptedException {
        final Process jps = new ProcessBuilder("jps", "-l").start();
        if (jps.waitFor() == 0) {
            return toString(jps.getInputStream());
        } else {
            throw new IllegalStateException(toString(jps.getErrorStream()));
        }
    }

    private static String toString(InputStream inputStream) throws IOException {
        try {
            Scanner scanner = new Scanner(inputStream, "UTF-8").useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        } finally {
            inputStream.close();
        }
    }

    private void attachToNewJvms(String jpsOutput) {
        final Set<JvmInfo> currentlyRunningJvms = getJVMs(jpsOutput);
        for (JvmInfo jvmInfo : getStartedJvms(currentlyRunningJvms)) {
            if (!jvmInfo.packageOrPath.endsWith(".Jps") && !jvmInfo.packageOrPath.isEmpty()) {
                onJvmStart(jvmInfo);
            }
        }
        runningJvms = currentlyRunningJvms;
    }

    @Nonnull
    private Set<JvmInfo> getJVMs(String jpsOutput) {
        Set<JvmInfo> set = new HashSet<>();
        for (String s : jpsOutput.split("\n")) {
            JvmInfo parse = JvmInfo.parse(s);
            set.add(parse);
        }
        return set;
    }

    private Set<JvmInfo> getStartedJvms(Set<JvmInfo> currentlyRunningJvms) {
        final HashSet<JvmInfo> newJvms = new HashSet<>(currentlyRunningJvms);
        newJvms.removeAll(runningJvms);
        return newJvms;
    }

    private void onJvmStart(JvmInfo jvmInfo) {
        if (isIncluded(jvmInfo) && !isExcluded(jvmInfo)) {
            try {
                final String agentArgs = getAgentArgs(jvmInfo);
                log("INFO", "Attaching the Elastic APM agent to %s with arguments %s", jvmInfo, agentArgs);
                ElasticApmAttacher.attach(jvmInfo.pid, agentArgs);
                log("INFO", "Done");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            log("DEBUG", "Not attaching the Elastic APM agent to %s, because it is not included or excluded.", jvmInfo);
        }
    }

    private String getAgentArgs(JvmInfo jvmInfo) throws IOException, InterruptedException {
        return arguments.getArgsProvider() != null ? getArgsProviderOutput(jvmInfo) : ElasticApmAttacher.toAgentArgs(arguments.getConfig());
    }

    private String getArgsProviderOutput(JvmInfo jvmInfo) throws IOException, InterruptedException {
        final Process jps = new ProcessBuilder(arguments.getArgsProvider(), jvmInfo.pid, jvmInfo.packageOrPath).start();
        if (jps.waitFor() == 0) {
            return toString(jps.getInputStream());
        } else {
            log("INFO", "Not attaching the Elastic APM agent to %s, " +
                "because the '--args-provider %s' script ended with a non-zero status code.", jvmInfo, arguments.argsProvider);
            throw new IllegalStateException(toString(jps.getErrorStream()));
        }
    }

    private boolean isIncluded(JvmInfo jvmInfo) {
        if (arguments.getIncludes().isEmpty()) {
            return true;
        }
        for (String include : arguments.getIncludes()) {
            if (jvmInfo.packageOrPath.matches(include)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExcluded(JvmInfo jvmInfo) {
        for (String exclude : arguments.getExcludes()) {
            if (jvmInfo.packageOrPath.matches(exclude)) {
                return true;
            }
        }
        return false;
    }

    static class JvmInfo {
        final String pid;
        final String packageOrPath;

        JvmInfo(String pid, String packageOrPath) {
            this.pid = pid;
            this.packageOrPath = packageOrPath;
        }

        static JvmInfo parse(String jpsLine) {
            final int firstSpace = jpsLine.indexOf(' ');
            return new JvmInfo(jpsLine.substring(0, firstSpace), jpsLine.substring(firstSpace + 1));
        }

        @Override
        public String toString() {
            return "JvmInfo{" +
                "pid='" + pid + '\'' +
                ", packageOrPath='" + packageOrPath + '\'' +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JvmInfo jvmInfo = (JvmInfo) o;
            return pid.equals(jvmInfo.pid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pid);
        }
    }

    static class Arguments {
        private final String pid;
        private final List<String> includes;
        private final List<String> excludes;
        private final Map<String, String> config;
        private final String argsProvider;
        private final boolean help;
        private final boolean list;
        private final boolean continuous;

        private Arguments(String pid, List<String> includes, List<String> excludes, Map<String, String> config, String argsProvider, boolean help, boolean list, boolean continuous) {
            this.help = help;
            this.list = list;
            this.continuous = continuous;
            if (!config.isEmpty() && argsProvider != null) {
                throw new IllegalArgumentException("Providing both --config and --args-provider is illegal");
            }
            if (pid != null && (!includes.isEmpty() || !excludes.isEmpty() || continuous)) {
                throw new IllegalArgumentException("Providing --pid and either of --include, --exclude or --continuous is illegal");
            }
            this.pid = pid;
            this.includes = includes;
            this.excludes = excludes;
            this.config = config;
            this.argsProvider = argsProvider;
        }

        static Arguments parse(String... args) {
            String pid = null;
            List<String> includes = new ArrayList<>();
            List<String> excludes = new ArrayList<>();
            Map<String, String> config = new HashMap<>();
            String argsProvider = null;
            boolean help = args.length == 0;
            boolean list = false;
            boolean continuous = false;
            String currentArg = "";
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
                        case "-p":
                        case "--pid":
                        case "-a":
                        case "--args":
                        case "-C":
                        case "--config":
                        case "-A":
                        case "--args-provider":
                        case "-e":
                        case "--exclude":
                        case "-i":
                        case "--include":
                            break;
                        default:
                            throw new IllegalArgumentException("Illegal argument: " + arg);
                    }
                } else {
                    switch (currentArg) {
                        case "-e":
                        case "--exclude":
                            excludes.add(arg);
                            break;
                        case "-i":
                        case "--include":
                            includes.add(arg);
                            break;
                        case "-p":
                        case "--pid":
                            pid = arg;
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
                        default:
                            throw new IllegalArgumentException("Illegal argument: " + arg);
                    }
                }
            }
            return new Arguments(pid, includes, excludes, config, argsProvider, help, list, continuous);
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
            out.println("    java -jar apm-agent-attach.jar -p <pid> [--args <agent_arguments>]");
            out.println("    java -jar apm-agent-attach.jar [-i <include_pattern>...] [-e <exclude_pattern>...] [--continuous]");
            out.println("                                   [--config <key=value>... | --args-provider <args_provider_script>]");
            out.println("    java -jar apm-agent-attach.jar (--list | --help)");
            out.println();
            out.println("DESCRIPTION");
            out.println("    Attaches the Elastic APM Java agent to a JVM with a specific PID or runs continuously and attaches to all running and starting JVMs which match the filters.");
            out.println();
            out.println("OPTIONS");
            out.println("    -l, --list");
            out.println("        Lists all running JVMs. Same output as 'jps -l'.");
            out.println();
            out.println("    -p, --pid <pid>");
            out.println("        PID of the JVM to attach. If not provided, attaches to all currently running JVMs which match the --exclude and --include filters.");
            out.println();
            out.println("    -c, --continuous");
            out.println("        If provided, this program continuously runs and attaches to all running and starting JVMs which match the --exclude and --include filters.");
            out.println();
            out.println("    -e, --exclude <exclude_pattern>...");
            out.println("        A list of regular expressions of fully qualified main class names or paths to JARs of applications the java agent should not be attached to.");
            out.println("        (Matches the output of 'jps -l')");
            out.println();
            out.println("    -i, --include <include_pattern>...");
            out.println("        A list of regular expressions of fully qualified main class names or paths to JARs of applications the java agent should be attached to.");
            out.println("        (Matches the output of 'jps -l')");
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
            out.println("        The program gets the pid and the main class name or path to the JAR file as an argument");
            out.println("        and returns an arg string which is used to configure the agent on the attached JVM (agentArguments of agentmain).");
            out.println("        When returning a non-zero status code from this program, the agent will not be attached to the starting JVM.");
            out.println("        The syntax of the arguments is 'key1=value1;key2=value1,value2'.");
            out.println("        Note: this option can not be used in conjunction with --pid and --args.");
        }

        String getPid() {
            return pid;
        }

        List<String> getIncludes() {
            return includes;
        }

        List<String> getExcludes() {
            return excludes;
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
    }

}
