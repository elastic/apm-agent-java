package co.elastic.apm.spring.boot.webflux;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface HelloRepository extends ReactiveCrudRepository<HelloEntity, Integer> {
}
