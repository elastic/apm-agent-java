package co.elastic.apm.agent.hibernate.search.v5_x;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class EntityManagerFactoryHelper {

    public static EntityManagerFactory buildEntityManagerFactory(final Path tempDirectory) {
        Map<String, Object> configOverrides = new HashMap<>();
        configOverrides.put("hibernate.search.default.indexBase", tempDirectory.toAbsolutePath().toString());
        return Persistence.createEntityManagerFactory("templatePU", configOverrides);
    }
}
