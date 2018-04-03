/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.impl.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;


/**
 * Information about the instrumented Service
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Service {

    /**
     * Name and version of the Elastic APM agent
     * (Required)
     */
    @Nullable
    @JsonProperty("agent")
    private Agent agent;
    /**
     * Name and version of the web framework used
     */
    @Nullable
    @JsonProperty("framework")
    private Framework framework;
    /**
     * Name and version of the programming language used
     */
    @Nullable
    @JsonProperty("language")
    private Language language;
    /**
     * Immutable name of the service emitting this event
     * (Required)
     */
    @Nullable
    @JsonProperty("name")
    private String name;
    /**
     * Environment name of the service, e.g. "production" or "staging"
     */
    @Nullable
    @JsonProperty("environment")
    private String environment;
    /**
     * Name and version of the language runtime running this service
     */
    @Nullable
    @JsonProperty("runtime")
    private RuntimeInfo runtime;
    /**
     * Version of the service emitting this event
     */
    @Nullable
    @JsonProperty("version")
    private String version;

    /**
     * Name and version of the Elastic APM agent
     * (Required)
     */
    @Nullable
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
    @Nullable
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
    @Nullable
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
    @Nullable
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
    @Nullable
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
    @Nullable
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
    @Nullable
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

}
