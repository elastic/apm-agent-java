package co.elastic.apm.agent.configuration;

import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ServiceLoader;

import static co.elastic.apm.agent.configuration.ConfigurationExporterTest.renderDocumentation;

public class ConfigurationExporter {

    public static void main(String[] args) throws Exception {
        ConfigurationRegistry configurationRegistry = ConfigurationRegistry.builder()
            .optionProviders(ServiceLoader.load(ConfigurationOptionProvider.class))
            .build();
        Path path = Paths.get("docs/configuration.asciidoc");
        if (!path.toFile().canWrite()) {
            throw new IllegalStateException(path + " does not exist");
        }
        Files.write(path, renderDocumentation(configurationRegistry).getBytes());
    }

}
