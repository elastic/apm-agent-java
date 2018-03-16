
package co.elastic.apm.impl.transaction;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import javax.annotation.Nullable;


/**
 * An object containing contextual data for database spans
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Db implements Recyclable {

    /**
     * Database instance name
     */
    @Nullable
    @JsonProperty("instance")
    private String instance;
    /**
     * A database statement (e.g. query) for the given database type
     */
    @Nullable
    @JsonProperty("statement")
    private String statement;
    /**
     * Database type. For any SQL database, "sql". For others, the lower-case database category, e.g. "cassandra", "hbase", or "redis"
     */
    @Nullable
    @JsonProperty("type")
    private String type;
    /**
     * Username for accessing database
     */
    @Nullable
    @JsonProperty("user")
    private String user;

    /**
     * Database instance name
     */
    @Nullable
    @JsonProperty("instance")
    public String getInstance() {
        return instance;
    }

    /**
     * Database instance name
     */
    public Db withInstance(@Nullable String instance) {
        this.instance = instance;
        return this;
    }

    /**
     * A database statement (e.g. query) for the given database type
     */
    @Nullable
    @JsonProperty("statement")
    public String getStatement() {
        return statement;
    }

    /**
     * A database statement (e.g. query) for the given database type
     */
    public Db withStatement(@Nullable String statement) {
        this.statement = statement;
        return this;
    }

    /**
     * Database type. For any SQL database, "sql". For others, the lower-case database category, e.g. "cassandra", "hbase", or "redis"
     */
    @Nullable
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    /**
     * Database type. For any SQL database, "sql". For others, the lower-case database category, e.g. "cassandra", "hbase", or "redis"
     */
    public Db withType(@Nullable String type) {
        this.type = type;
        return this;
    }

    /**
     * Username for accessing database
     */
    @Nullable
    @JsonProperty("user")
    public String getUser() {
        return user;
    }

    /**
     * Username for accessing database
     */
    public Db withUser(@Nullable String user) {
        this.user = user;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("instance", instance)
            .append("statement", statement)
            .append("type", type)
            .append("user", user).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(statement)
            .append(instance)
            .append(type)
            .append(user).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Db) == false) {
            return false;
        }
        Db rhs = ((Db) other);
        return new EqualsBuilder()
            .append(statement, rhs.statement)
            .append(instance, rhs.instance)
            .append(type, rhs.type)
            .append(user, rhs.user).isEquals();
    }

    @Override
    public void resetState() {
        instance = null;
        statement = null;
        type = null;
        user = null;
    }
}
