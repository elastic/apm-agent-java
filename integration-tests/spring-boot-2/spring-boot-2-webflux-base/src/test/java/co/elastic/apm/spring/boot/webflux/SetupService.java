package co.elastic.apm.spring.boot.webflux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;


@Service
public class SetupService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(SetupService.class);

    @Value("classpath:h2/init.sql")
    private Resource initSql;

    @Autowired
    private R2dbcEntityTemplate r2dbcEntityTemplate;

    @Override
    public void run(String... args) throws Exception {
        String query = StreamUtils.copyToString(initSql.getInputStream(), StandardCharsets.UTF_8);
        logger.info("Trying to setup db tables via sql = {}", query);
        this.r2dbcEntityTemplate
            .getDatabaseClient()
            .sql(query)
            .then()
            .subscribe();
        logger.info("Data setup success.");
    }
}
