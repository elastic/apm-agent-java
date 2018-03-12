
package co.elastic.apm.impl.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Information about the instrumented Service
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
// TODO: make immutable
public class Service {

    /**
     * Name and version of the Elastic APM agent
     * (Required)
     */
    @JsonProperty("agent")
    private Agent agent;
    /**
     * Name and version of the web framework used
     */
    @JsonProperty("framework")
    private Framework framework;
    /**
     * Name and version of the programming language used
     */
    @JsonProperty("language")
    private Language language;
    /**
     * Immutable name of the service emitting this event
     * (Required)
     */
    @JsonProperty("name")
    private String name;
    /**
     * Environment name of the service, e.g. "production" or "staging"
     */
    @JsonProperty("environment")
    private String environment;
    /**
     * Name and version of the language runtime running this service
     */
    @JsonProperty("runtime")
    private RuntimeInfo runtime;
    /**
     * Version of the service emitting this event
     */
    @JsonProperty("version")
    private String version;

    /**
     * Name and version of the Elastic APM agent
     * (Required)
     */
    @JsonProperty("agent")
    public Agent getAgent() {
        return agent;
    }

    /**
     * Name and version of the Elastic APM agent
     * (Required)
     */
    public Service withAgent(Agent agent) {
        this.agent = agent;
        return this;
    }

    /**
     * Name and version of the web framework used
     */
    @JsonProperty("framework")
    public Framework getFramework() {
        return framework;
    }

    /**
     * Name and version of the web framework used
     */
    public Service withFramework(Framework framework) {
        this.framework = framework;
        return this;
    }

    /**
     * Name and version of the programming language used
     */
    @JsonProperty("language")
    public Language getLanguage() {
        return language;
    }

    /**
     * Name and version of the programming language used
     */
    public Service withLanguage(Language language) {
        this.language = language;
        return this;
    }

    /**
     * Immutable name of the service emitting this event
     * (Required)
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * Immutable name of the service emitting this event
     * (Required)
     */
    public Service withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Environment name of the service, e.g. "production" or "staging"
     */
    @JsonProperty("environment")
    public String getEnvironment() {
        return environment;
    }

    /**
     * Environment name of the service, e.g. "production" or "staging"
     */
    public Service withEnvironment(String environment) {
        this.environment = environment;
        return this;
    }

    /**
     * Name and version of the language runtime running this service
     */
    @JsonProperty("runtime")
    public RuntimeInfo getRuntime() {
        return runtime;
    }

    /**
     * Name and version of the language runtime running this service
     */
    public Service withRuntime(RuntimeInfo runtime) {
        this.runtime = runtime;
        return this;
    }

    /**
     * Version of the service emitting this event
     */
    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

    /**
     * Version of the service emitting this event
     */
    public Service withVersion(String version) {
        this.version = version;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("agent", agent).append("framework", framework).append("language", language).append("name", name).append("environment", environment).append("runtime", runtime).append("version", version).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(agent).append(environment).append(framework).append(name).append(runtime).append(language).append(version).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Service) == false) {
            return false;
        }
        Service rhs = ((Service) other);
        return new EqualsBuilder().append(agent, rhs.agent).append(environment, rhs.environment).append(framework, rhs.framework).append(name, rhs.name).append(runtime, rhs.runtime).append(language, rhs.language).append(version, rhs.version).isEquals();
    }

}
