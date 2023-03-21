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
package co.elastic.apm.agent;

import co.elastic.apm.agent.bci.PluginClassLoaderRootPackageCustomizer;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class PackagingTest {

    private static final Logger log = LoggerFactory.getLogger(PackagingTest.class);

    private static final Path MODULE_ROOT = getModuleRoot();
    private static final int MAX_DEPTH = 99;

    @Test
    void checkPluginDependencies() {
        Set<String> pluginArtifactIds = getAgentPluginModules()
            .filter(AgentModule::isPlugin)
            .map(m -> m.mavenArtifactId)
            .collect(Collectors.toSet());

        checkContainsPluginAsDependencies(MODULE_ROOT.resolve("pom.xml"), pluginArtifactIds);
    }

    @Test
    void checkPluginInterdependencies() {

        Stream<AgentModule> plugins = getAgentPluginModules().filter(AgentModule::isPlugin);
        Map<String, AgentModule> modules = getAgentPluginModules().collect(Collectors.toMap(m -> m.mavenArtifactId, m -> m));
        plugins.forEach(plugin -> {

            Set<AgentModule> dependencies = plugin.getInternalDependencies().stream()
                .filter(d -> !d.equals("apm-agent-core")) // filter-out explicit dependencies to apm-agent-core
                .filter(d -> !d.equals("apm-opentelemetry-embedded-metrics-sdk")) // embedded metricsdk is intentionally loaded by agent classloader
                .map(modules::get)
                .collect(Collectors.toSet());


            for (AgentModule dep : dependencies) {
                log.info("checking dependency from plugin '{}' to module '{}'", plugin.mavenArtifactId, dep.mavenArtifactId);

                if (plugin.basePackage.equals(dep.basePackage)) {
                    log.info("plugin '{}' with base package '{}' depends on module '{}' that have same base package", plugin.mavenArtifactId, plugin.basePackage, dep.mavenArtifactId);
                } else {
                    log.info("plugin '{}' with base package '{}' depends on module '{}' with base package '{}'", plugin.mavenArtifactId, plugin.basePackage, dep.mavenArtifactId, dep.basePackage);

                    Collection<String> depRootPackages = dep.getClassloaderRootPackages();
                    if (depRootPackages.isEmpty()) {
                        // dependency is loaded in the bootstrap CL, thus it is always visible to the plugin
                        log.info("dependency '{}' is loaded in the bootstrap CL, thus it is always visible to the '{}' plugin", dep.mavenArtifactId, plugin.mavenArtifactId);
                    } else if (!dep.hasNonTestDependencies()) {
                        log.info("dependency '{}' does not have dependencies, thus it is safe to load from agent or plugin CL", dep.mavenArtifactId);
                    } else if (plugin.getClassloaderRootPackages().contains(dep.basePackage)) {
                        log.info("dependency '{}' is accessible to plugin '{}' through classloader root customization", dep.mavenArtifactId, plugin.mavenArtifactId);
                    } else {
                        fail("dependency '%s' base package '%s' is NOT accessible to plugin '%s', using a PluginClassLoaderRootPackageCustomizer in plugin is required", dep.mavenArtifactId, dep.basePackage, plugin.mavenArtifactId);
                    }

                }
            }

        });

    }

    private static Stream<AgentModule> getAgentPluginModules() {
        // search for all maven  submodules within the plugins directory

        Path pluginsModulePath = MODULE_ROOT.getParent().resolve("apm-agent-plugins");

        Stream<Path> pomStream;
        try {
            pomStream = Files.find(pluginsModulePath, MAX_DEPTH,
                    (path, attributes) -> path.getFileName().toString().equals("pom.xml"))
                .collect(Collectors.toSet())
                .stream();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return pomStream
            // use an intermediate map entry to keep track of file -> pom association
            .map(file -> Map.entry(file, parseMaven(file)))
            // only include real code dependencies, pom modules can be ignored
            .filter(e -> e.getValue().getPackaging() == null || e.getValue().getPackaging().equals("jar"))
            .map(e -> new AgentModule(e.getKey().getParent()));
    }

    /**
     * Represents a Java agent module, either a plugin or an internal dependency
     */
    private static class AgentModule {
        private final Path rootFolder;
        private final Model mavenModel;
        private final String mavenGroupId;
        private final String mavenArtifactId;

        @Nullable
        private final String basePackage;

        private AgentModule(Path rootFolder) {
            this.rootFolder = rootFolder.toAbsolutePath();
            this.mavenModel = parseMaven(rootFolder.resolve("pom.xml"));
            this.mavenArtifactId = mavenModel.getArtifactId();
            this.basePackage = getBasePackage(rootFolder);

            // verify module invariants & store effective groupID
            String groupId = mavenModel.getGroupId();
            if (groupId == null) {
                assertThat(mavenModel.getParent()).isNotNull();
                groupId = mavenModel.getParent().getGroupId();
            }
            this.mavenGroupId = groupId;
            assertThat(groupId)
                .isEqualTo("co.elastic.apm");
        }

        public boolean isPlugin() {
            try {
                Path servicesFolder = getServicesFolder();
                return servicesFolder != null && Files.list(servicesFolder).count() > 0;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public boolean hasCode() {
            return basePackage != null;
        }

        public Collection<String> getClassloaderRootPackages() {
            Path servicesFolder = getServicesFolder();
            if (servicesFolder != null) {
                Path customizerService = servicesFolder.resolve(PluginClassLoaderRootPackageCustomizer.class.getName());
                if (Files.exists(customizerService)) {
                    try {
                        List<String> lines = Files.readAllLines(customizerService);
                        assertThat(lines).describedAs("exactly one root package customizer expected in file %s", customizerService).hasSize(1);
                        Class<?> customizerType = Class.forName(lines.get(0), true, PackagingTest.class.getClassLoader());
                        PluginClassLoaderRootPackageCustomizer customizer = (PluginClassLoaderRootPackageCustomizer) customizerType.getConstructor().newInstance();
                        return customizer.pluginClassLoaderRootPackages();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
            return Collections.singleton(basePackage);
        }

        @Nullable
        private Path getServicesFolder() {
            Path servicesFolder = rootFolder.resolve(Path.of("src", "main", "resources", "META-INF", "services"));
            return Files.isDirectory(servicesFolder) ? servicesFolder : null;
        }

        public Set<String> getInternalDependencies() {
            return mavenModel.getDependencies()
                .stream()
                .filter(d -> getGroupId(d).equals(mavenGroupId))
                .filter(d -> d.getScope() == null || !d.getScope().equals("test"))
                .map(Dependency::getArtifactId)
                .collect(Collectors.toSet());
        }

        public boolean hasNonTestDependencies() {
            return mavenModel.getDependencies().stream().anyMatch(d -> !"test".equals(d.getScope()));
        }

        private String getGroupId(Dependency d) {
            String groupId = d.getGroupId();
            return groupId.equals("${project.groupId}") ? mavenGroupId : groupId;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AgentModule that = (AgentModule) o;
            return rootFolder.equals(that.rootFolder);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rootFolder);
        }

        @Override
        public String toString() {
            return "AgentPlugin{" +
                "rootFolder=" + rootFolder +
                ", mavenArtifactId='" + mavenArtifactId + '\'' +
                ", basePackage='" + basePackage + '\'' +
                '}';
        }
    }

    private static Model parseMaven(Path pomPath) {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try (Reader r = Files.newBufferedReader(pomPath)) {
            return reader.read(r);
        } catch (IOException | XmlPullParserException e) {
            throw new IllegalStateException(e);
        }

    }

    @Nullable
    private static String getBasePackage(Path pluginRoot) {
        Path javaSources = pluginRoot.resolve("src").resolve("main").resolve("java");

        if (!Files.isDirectory(javaSources)) {
            // no java sources, it s a test module
            return null;
        }

        Path expectedPrefix = javaSources.resolve("co").resolve("elastic").resolve("apm").resolve("agent");
        AtomicReference<String> pluginSubPackage = new AtomicReference<>();

        try {
            Files.find(javaSources, MAX_DEPTH, (path, attributes) ->
                    Files.isRegularFile(path)
                        && !path.getFileName().toString().equals("module-info.java")
                        && path.getFileName().toString().endsWith(".java"))
                .forEach(p -> {
                    assertThat(p)
                        .describedAs("unexpected plugin file location '%s' should be in '%s", p, expectedPrefix)
                        .startsWith(expectedPrefix);

                    Path relativePath = expectedPrefix.relativize(p);
                    String subPackage = relativePath.getName(0).toString();
                    if (null == pluginSubPackage.get()) {
                        pluginSubPackage.set(subPackage);
                    } else {
                        assertThat(subPackage).isEqualTo(pluginSubPackage.get());
                    }
                });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        assertThat(pluginSubPackage.get()).isNotNull();
        return String.format("co.elastic.apm.agent.%s", pluginSubPackage.get());
    }

    private static Path getModuleRoot() {
        Path classLocation;
        try {
            classLocation = Paths.get(PackagingTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        Path moduleRoot = classLocation.getParent().getParent().getParent();
        assertThat(moduleRoot).isDirectory();
        return moduleRoot;
    }

    private static void checkContainsPluginAsDependencies(Path pomPath, Set<String> plugins) {
        assertThat(pomPath).isRegularFile();

        Model model = parseMaven(pomPath);
        Set<String> dependenciesArtifacts = model.getDependencies().stream()
            .filter(d -> sameProjectGroup(model, d) && sameProjectVersion(model, d))
            .map(Dependency::getArtifactId)
            .collect(Collectors.toSet());

        assertThat(dependenciesArtifacts).describedAs(createDescription(plugins)).containsAll(plugins);

    }

    private static String createDescription(Set<String> plugins) {
        StringBuilder sb = new StringBuilder();

        sb.append("\n fix apm-agent dependencies with following snippet:\n");

        ArrayList<String> list = new ArrayList<>(plugins);
        list.sort(Comparator.naturalOrder());
        for (String s : list) {
            sb.append("<dependency>\n")
                .append("    <groupId>${project.groupId}</groupId>\n")
                .append("    <artifactId>").append(s).append("</artifactId>\n")
                .append("    <version>${project.version}</version>\n")
                .append("</dependency>\n");
        }

        return sb.toString();
    }

    private static boolean sameProjectVersion(Model model, Dependency d) {
        String value = d.getVersion();
        return value.equals(model.getVersion()) || value.equals("${project.version}");
    }

    private static boolean sameProjectGroup(Model model, Dependency d) {
        String value = d.getGroupId();
        return value.equals(model.getGroupId()) || value.equals("${project.groupId}");
    }
}
