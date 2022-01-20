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
package co.elastic.apm.agent.impl.circuitbreaker;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.util.JmxUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SystemCpuStressMonitor extends StressMonitor {

    private static final Logger logger = LoggerFactory.getLogger(SystemCpuStressMonitor.class);

    private final StringBuilder latestStressDetectionInfo = new StringBuilder("No stress has been detected so far.");

    private final OperatingSystemMXBean operatingSystemBean;

    private int consecutiveMeasurementsAboveStressThreshold;
    private int consecutiveMeasurementsBelowReliefThreshold;

    private boolean currentlyUnderStress;

    @Nullable
    private final Method systemCpuUsageMethod;

    SystemCpuStressMonitor(ElasticApmTracer tracer) {
        super(tracer);
        operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();
        systemCpuUsageMethod = JmxUtils.getOperatingSystemMBeanMethod(operatingSystemBean, "getSystemCpuLoad");
        if (systemCpuUsageMethod != null) {
            logger.debug("Successfully obtained reference to the getSystemCpuLoad method of this JVM's OperatingSystemMXBean implementation");
        } else {
            logger.warn("Failed to obtain reference to the getSystemCpuLoad method of this JVM's OperatingSystemMXBean implementation");
        }
    }

    @Nullable
    Method getGetSystemCpuLoadMethod() {
        return systemCpuUsageMethod;
    }

    OperatingSystemMXBean getOperatingSystemBean() {
        return operatingSystemBean;
    }

    @Override
    boolean isUnderStress() throws Exception {
        readAndCompareToThresholds();
        return currentlyUnderStress;
    }

    @Override
    boolean isStressRelieved() throws Exception {
        readAndCompareToThresholds();
        return !currentlyUnderStress;
    }

    /**
     * Reads the recent system CPU load, compares to thresholds and updates the state accordingly.
     * This monitor can be in one of two states - either under detected stress or not.
     * The switch from a non-stress state to a stress state requires consecutive number of measurements that cross the
     * configured stress threshold. The switch from a stress state to a non-stress state requires consecutive
     * measurements below the relief threshold
     * @throws InvocationTargetException indicates a failure to invoke the getSystemCpuLoad method through reflection
     * @throws IllegalAccessException if this class doesn't have the proper privileges to read from JMX
     */
    private void readAndCompareToThresholds() throws InvocationTargetException, IllegalAccessException {
        Method mbeanMethodImpl = getGetSystemCpuLoadMethod();
        if (mbeanMethodImpl != null) {
            double systemCpuValue = ((Number) mbeanMethodImpl.invoke(getOperatingSystemBean())).doubleValue();
            if (!Double.isNaN(systemCpuValue) && !Double.isInfinite(systemCpuValue)) {
                logger.debug("System CPU measurement: {}", systemCpuValue);
                int cpuConsecutiveMeasurements = (int) (circuitBreakerConfiguration.getCpuStressDurationThresholdMillis() /
                                        circuitBreakerConfiguration.getStressMonitoringPollingIntervalMillis());
                if (systemCpuValue > circuitBreakerConfiguration.getSystemCpuStressThreshold()) {
                    consecutiveMeasurementsAboveStressThreshold++;
                    if (consecutiveMeasurementsAboveStressThreshold == cpuConsecutiveMeasurements) {
                        // Change the state to indicate current stress
                        currentlyUnderStress = true;
                        latestStressDetectionInfo.setLength(0);
                        latestStressDetectionInfo.append("Latest system CPU load value measured is ").append(systemCpuValue)
                            .append(". This is the ").append(cpuConsecutiveMeasurements)
                            .append("th consecutive measurement that crossed the configured stress threshold - ")
                            .append(circuitBreakerConfiguration.getSystemCpuStressThreshold())
                            .append(", which indicates this host is under CPU stress.");
                    }
                } else {
                    consecutiveMeasurementsAboveStressThreshold = 0;
                }

                if (systemCpuValue < circuitBreakerConfiguration.getSystemCpuReliefThreshold()) {
                    consecutiveMeasurementsBelowReliefThreshold++;
                    if (consecutiveMeasurementsBelowReliefThreshold == cpuConsecutiveMeasurements) {
                        // Change the state to indicate we are currently not under stress
                        currentlyUnderStress = false;
                        logger.info("Latest system CPU load value measured is {}. This is {}th consecutive measurement that is below the configured relief threshold - {}",
                            systemCpuValue, cpuConsecutiveMeasurements, circuitBreakerConfiguration.getSystemCpuReliefThreshold());
                    }
                } else {
                    consecutiveMeasurementsBelowReliefThreshold = 0;
                }
            } else {
                logger.debug("Latest measurement of system CPU load produced an invalid value: {}", systemCpuValue);
            }
        }
    }

    @Override
    String getStressDetectionInfo() {
        return latestStressDetectionInfo.toString();
    }
}
