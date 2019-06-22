package co.elastic.apm.agent.hibernate.search.v6_x;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class EntityManagerFactoryHelper {

    public static EntityManagerFactory buildEntityManagerFactory(final Path tempDirectory) {
        Map<String, Object> configOverrides = new HashMap<>();
        configOverrides.put("hibernate.search.backends.testBackend.type", "lucene");
        configOverrides.put("hibernate.search.backends.testBackend.directory_provider", "local_directory");
        configOverrides.put("hibernate.search.backends.testBackend.root_directory", tempDirectory.toAbsolutePath().toString());
        configOverrides.put("hibernate.search.default_backend", "testBackend");
        return Persistence.createEntityManagerFactory("templatePU", configOverrides);
    }
}
