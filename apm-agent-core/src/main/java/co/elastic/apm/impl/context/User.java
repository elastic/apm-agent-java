
package co.elastic.apm.impl.context;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;


/**
 * User
 * <p>
 * Describes the authenticated User for a request.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User implements Recyclable {

    /**
     * Identifier of the logged in user, e.g. the primary key of the user
     */
    @Nullable
    @JsonProperty("id")
    private String id;
    /**
     * Email of the logged in user
     */
    @Nullable
    @JsonProperty("email")
    private String email;
    /**
     * The username of the logged in user
     */
    @Nullable
    @JsonProperty("username")
    private String username;

    /**
     * Identifier of the logged in user, e.g. the primary key of the user
     */
    @Nullable
    @JsonProperty("id")
    public String getId() {
        return id;
    }

    /**
     * Identifier of the logged in user, e.g. the primary key of the user
     */
    public User withId(@Nullable String id) {
        this.id = id;
        return this;
    }

    /**
     * Email of the logged in user
     */
    @Nullable
    @JsonProperty("email")
    public String getEmail() {
        return email;
    }

    /**
     * Email of the logged in user
     */
    public User withEmail(@Nullable String email) {
        this.email = email;
        return this;
    }

    /**
     * The username of the logged in user
     */
    @Nullable
    @JsonProperty("username")
    public String getUsername() {
        return username;
    }

    /**
     * The username of the logged in user
     */
    public User withUsername(@Nullable String username) {
        this.username = username;
        return this;
    }

    @Override
    public void resetState() {
        id = null;
        email = null;
        username = null;
    }

    public void copyFrom(User other) {
        this.email = other.email;
        this.id = other.id;
        this.username = other.username;
    }
}
