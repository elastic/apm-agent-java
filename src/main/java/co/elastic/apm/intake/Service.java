
package co.elastic.apm.intake;

import co.elastic.apm.intake.errors.Agent;
import co.elastic.apm.intake.errors.Framework;
import co.elastic.apm.intake.errors.Language;
import co.elastic.apm.intake.errors.Runtime;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Service
 * <p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "agent",
    "framework",
    "language",
    "name",
    "environment",
    "runtime",
    "version"
})
// TODO: make immutable
public class Service {

    /**
     * Name and version of the Elastic APM agent
     * (Required)
     */
    @JsonProperty("agent")
    @JsonPropertyDescription("Name and version of the Elastic APM agent")
    private Agent agent;
    /**
     * Name and version of the web framework used
     */
    @JsonProperty("framework")
    @JsonPropertyDescription("Name and version of the web framework used")
    private Framework framework;
    /**
     * Name and version of the programming language used
     */
    @JsonProperty("language")
    @JsonPropertyDescription("Name and version of the programming language used")
    private Language language;
    /**
     * Immutable name of the service emitting this event
     * (Required)
     */
    @JsonProperty("name")
    @JsonPropertyDescription("Immutable name of the service emitting this event")
    private String name;
    /**
     * Environment name of the service, e.g. "production" or "staging"
     */
    @JsonProperty("environment")
    @JsonPropertyDescription("Environment name of the service, e.g. \"production\" or \"staging\"")
    private String environment;
    /**
     * Name and version of the language runtime running this service
     */
    @JsonProperty("runtime")
    @JsonPropertyDescription("Name and version of the language runtime running this service")
    private Runtime runtime;
    /**
     * Version of the service emitting this event
     */
    @JsonProperty("version")
    @JsonPropertyDescription("Version of the service emitting this event")
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
    @JsonProperty("agent")
    public void setAgent(Agent agent) {
        this.agent = agent;
    }

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
    @JsonProperty("framework")
    public void setFramework(Framework framework) {
        this.framework = framework;
    }

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
    @JsonProperty("language")
    public void setLanguage(Language language) {
        this.language = language;
    }

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
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

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
    @JsonProperty("environment")
    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Service withEnvironment(String environment) {
        this.environment = environment;
        return this;
    }

    /**
     * Name and version of the language runtime running this service
     */
    @JsonProperty("runtime")
    public Runtime getRuntime() {
        return runtime;
    }

    /**
     * Name and version of the language runtime running this service
     */
    @JsonProperty("runtime")
    public void setRuntime(Runtime runtime) {
        this.runtime = runtime;
    }

    public Service withRuntime(Runtime runtime) {
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
    @JsonProperty("version")
    public void setVersion(String version) {
        this.version = version;
    }

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
