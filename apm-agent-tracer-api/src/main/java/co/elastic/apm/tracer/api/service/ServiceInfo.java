/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.tracer.api.service;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class ServiceInfo {

    private static final String DEFAULT_SERVICE_NAME = "unknown-java-service";
    private static final ServiceInfo EMPTY = new ServiceInfo(null, null);

    protected final String serviceName;
    @Nullable
    protected final String serviceVersion;
    protected final boolean multiServiceContainer;

    public ServiceInfo(@Nullable String serviceName) {
        this(serviceName, null);
    }

    private ServiceInfo(@Nullable String serviceName, @Nullable String serviceVersion) {
        this(serviceName, serviceVersion, false);
    }

    protected ServiceInfo(@Nullable String serviceName, @Nullable String serviceVersion, boolean multiServiceContainer) {
        this.multiServiceContainer = multiServiceContainer;
        if (serviceName == null || serviceName.trim().isEmpty()) {
            this.serviceName = DEFAULT_SERVICE_NAME;
        } else {
            this.serviceName = replaceDisallowedServiceNameChars(serviceName).trim();
        }
        this.serviceVersion = serviceVersion;
    }

    public static ServiceInfo empty() {
        return EMPTY;
    }

    public static ServiceInfo of(@Nullable String serviceName) {
        return of(serviceName, null);
    }

    public static ServiceInfo of(@Nullable String serviceName, @Nullable String serviceVersion) {
        if ((serviceName == null || serviceName.isEmpty()) &&
            (serviceVersion == null || serviceVersion.isEmpty())) {
            return empty();
        }
        return new ServiceInfo(serviceName, serviceVersion);
    }

    private static String replaceDisallowedServiceNameChars(String serviceName) {
        return serviceName.replaceAll("[^a-zA-Z0-9 _-]", "-");
    }

    public static ServiceInfo fromManifest(@Nullable Manifest manifest) {
        if (manifest == null) {
            return ServiceInfo.empty();
        }
        Attributes mainAttributes = manifest.getMainAttributes();
        return ServiceInfo.of(
            mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE),
            mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION));
    }

    public static ServiceInfo autoDetected() {
        return null; // TODO
    }

    public String getServiceName() {
        return serviceName;
    }

    @Nullable
    public String getServiceVersion() {
        return serviceVersion;
    }

    /**
     * Returns true if the service is a container service that can host multiple other applications.
     * For example, an application server or servlet container.
     * A standalone application that's built on embedded Tomcat, for example, would return {@code false}.
     */
    public boolean isMultiServiceContainer() {
        return multiServiceContainer;
    }

    public ServiceInfo withFallback(ServiceInfo fallback) {
        return ServiceInfo.of(
            hasServiceName() ? serviceName : fallback.serviceName,
            serviceVersion != null ? serviceVersion : fallback.serviceVersion);
    }

    public boolean hasServiceName() {
        return !serviceName.equals(DEFAULT_SERVICE_NAME);
    }

    public boolean isEmpty() {
        return !hasServiceName() && serviceVersion == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInfo that = (ServiceInfo) o;
        return multiServiceContainer == that.multiServiceContainer && serviceName.equals(that.serviceName) && Objects.equals(serviceVersion, that.serviceVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, serviceVersion, multiServiceContainer);
    }

    @Override
    public String toString() {
        return "ServiceInfo{" +
            "serviceName='" + serviceName + '\'' +
            ", serviceVersion='" + serviceVersion + '\'' +
            ", multiServiceContainer=" + multiServiceContainer +
            '}';
    }
}
