package co.elastic.apm.spring.boot.webflux;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("hello")
public class HelloEntity {

    public HelloEntity() {
    }

    public HelloEntity(Integer id, String message) {
        this.id = id;
        this.message = message;
    }

    @Id
    private Integer id;
    private String message;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
