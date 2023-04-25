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
package co.elastic.apm.agent.micrometer;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.tracer.configuration.MetricsConfiguration;
import co.elastic.apm.agent.tracer.configuration.ReporterConfiguration;
import com.dslplatform.json.JsonWriter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.CountingMode;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.io.Closeable;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/*
MeterRegistrys with CountingMode CUMULATIVE are eventually accurate, and rarely misleading
- typically the metric values are immediately correct, but in the worst case are eventually
correct (ie the metric being written may be a bit behind the last update, but will good enough)

MeterRegistrys with CountingMode STEP update the accessible metric value each "step interval",
and in between they hold a cache of the last value, and that value is what is provided when
sampled. So if the agent metric interval is shorter, the agent will publish the cached
value at least once when it should be producing 0.

Here, the agent takes the step interval as primary if that is being used, otherwise the
agent interval is used. But note that because we don't know exactly when the meter is
updated, and the scheduler here can drift long (take more than 1 second between checks),
it's possible that the agent drops the occasional sample
 */
public class MicrometerMetricsReporter implements Runnable, Closeable {

    //interval is actually this plus a random amount of ms more due to processing delays
    //this means that an occasional step metric will be skipped. This is deemed acceptable
    private static final long INTERVAL_BETWEEN_CHECKS_IN_MILLISECONDS = 1000L;
    private static final Logger logger = LoggerFactory.getLogger(MicrometerMetricsReporter.class);
    private static final boolean HAS_SimpleMeterRegistry_METHOD;
    static {
        boolean hasSimpleMeterRegistryMethod1;
        try {
            Class<?> simpleMeterRegistryClass = Class.forName("io.micrometer.core.instrument.simple.SimpleMeterRegistry");
            simpleMeterRegistryClass.getDeclaredMethod("getMetersAsString", new Class[0]);
            hasSimpleMeterRegistryMethod1 = true;
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            hasSimpleMeterRegistryMethod1 = false;
        }
        HAS_SimpleMeterRegistry_METHOD = hasSimpleMeterRegistryMethod1;
    }

    private volatile long lastMetricIntervalTimestamp = System.currentTimeMillis();

    //Would have been cleaner to use MeterRegistryWrapper class, but they would get removed
    //immediately from the weak set because nothing else points at them
    private final WeakMap<MeterRegistry, Step> meterRegistries = WeakConcurrent.buildMap();
    private final WeakMap<MeterRegistry, SimpleConfig> configMap = WeakConcurrent.buildMap();
    private final MicrometerMeterRegistrySerializer serializer;
    private final Reporter reporter;
    private final ElasticApmTracer tracer;
    private final AtomicBoolean scheduledReporting = new AtomicBoolean();
    private final boolean disableScheduler;

    public MicrometerMetricsReporter(ElasticApmTracer tracer) {
        this(tracer, false);
    }

    //constructor split up to have this available for testing
    MicrometerMetricsReporter(ElasticApmTracer tracer, boolean disableSchedulerThread) {
        this.tracer = tracer;
        this.reporter = tracer.getReporter();
        tracer.addShutdownHook(this);
        serializer = new MicrometerMeterRegistrySerializer(tracer.getConfig(MetricsConfiguration.class));
        this.disableScheduler = disableSchedulerThread;
    }

    void registerMeterRegistry(MeterRegistry meterRegistry) {
        if (meterRegistry instanceof CompositeMeterRegistry) {
            return;
        }
        long step = getStep(meterRegistry);
        if (step >= 0 && step < 1000) {
            logger.debug("Not registering unsupported step interval of {} milliseconds (1 seconds is the minimum supported) for Micrometer MeterRegistry: {}", step, meterRegistry);
            return;
        }
        Step newStep = new Step(step);
        Step hopefullyNull = meterRegistries.putIfAbsent(meterRegistry, newStep);
        if (hopefullyNull != null) {
            logger.trace("Not re-registering MeterRegistry as it is already registered from another compound meter registry: {}", meterRegistry);
        } else {
            logger.info("Registering Micrometer MeterRegistry: {}", meterRegistry);
        }
        scheduleReporting();
    }

    private synchronized void scheduleReporting() {
        if (disableScheduler) {
            return;
        }
        if (scheduledReporting.compareAndSet(false, true)) {
            // called for every class loader that loaded micrometer
            // that's because a new MicrometerMetricsReporter instance is created in every IndyPluginClassLoader
            // for example if multiple webapps use potentially different versions of Micrometer
            tracer.getSharedSingleThreadedPool().scheduleAtFixedRate(this, 0, INTERVAL_BETWEEN_CHECKS_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
        }
    }

    // guaranteed to be invoked by a single thread
    @Override
    public void run() {
        run(System.currentTimeMillis());
    }

    //run split up to have this available for testing
    void run(final long now) {
        if (tracer.getState() != Tracer.TracerState.RUNNING) {
            return;
        }
        long metricsIntervalMs = tracer.getConfig(ReporterConfiguration.class).getMetricsIntervalMs();
        if (metricsIntervalMs == 0) {
            //by checking here rather than at thread creation/non-creation,
            //the interval can be dynamically updated and any new value will apply
            return;
        }

        //non-step metrics are reported every metricsInterval,
        //step metrics are reported every step interval (step defined by the registry)
        boolean reportNonStepMetrics = false;
        if((now - lastMetricIntervalTimestamp) >= metricsIntervalMs) {
            reportNonStepMetrics = true;
            //non-atomic, but this is used single-threaded, it's only
            //volatile for tests to be able to reset the value
            lastMetricIntervalTimestamp += metricsIntervalMs;
        }

        Iterator<Map.Entry<MeterRegistry, Step>> registriesIterator = meterRegistries.iterator();
        final Set<MeterRegistry> currentlyReportableRegistries = new HashSet<>();
        while(registriesIterator.hasNext()) {
            Map.Entry<MeterRegistry, Step> meterRegistryStepEntry = registriesIterator.next();
            MeterRegistry meterRegistry = meterRegistryStepEntry.getKey();
            Step meterRegistryStep = meterRegistryStepEntry.getValue();
            if (meterRegistryStep.isStep()) {
                logger.debug("Evaluating whether to report: step {}, interval(ms) {}, meterRegistry {}", meterRegistryStep.lastStepCount ,meterRegistryStep.stepInMs, meterRegistry);
                if (meterRegistryStep.shouldReportNow(now)) {
                    currentlyReportableRegistries.add(meterRegistry);
                    meterRegistryStep.incrementToNextStep(now);
                }
            } else if (reportNonStepMetrics) {
                currentlyReportableRegistries.add(meterRegistry);
            }
        }

        MeterMapConsumer meterConsumer = MeterMapConsumer.INSTANCE.reset(tracer.getConfig(ReporterConfiguration.class).getDisableMetrics());
        for (MeterRegistry registry : currentlyReportableRegistries) {
            registry.forEachMeter(meterConsumer);
        }
        logger.debug("Reporting {} meters", meterConsumer.meters.size());
        for (JsonWriter serializedMetricSet : serializer.serialize(meterConsumer.meters, now * 1000)) {
            reporter.reportMetrics(serializedMetricSet);
        }
    }

    private long getStep(MeterRegistry meterRegistry) {
        if (meterRegistry.config() instanceof StepRegistryConfig) {
            return ((StepRegistryConfig) (meterRegistry.config())).step().toMillis();
        }
        if (meterRegistry instanceof SimpleMeterRegistry) {
            if (!configMap.containsKey(meterRegistry)) {
                //Next is just used to trigger the side-effect of adding the config to the map
                //But it's only available from micrometer 1.9.0
                if (HAS_SimpleMeterRegistry_METHOD) {
                    ((SimpleMeterRegistry) meterRegistry).getMetersAsString();
                } else {
                    return -1;
                }
            }
            SimpleConfig config = configMap.get(meterRegistry);
            return (config != null && CountingMode.STEP.equals(config.mode())) ?
                config.step().toMillis() : -1;
        }
        //non-step registries
        return -1;
    }

    @Override
    public void close() {
        // flushing out metrics before shutting down
        // this is especially important for counters as the counts that were accumulated between the last report and the shutdown would otherwise get lost
        tracer.getSharedSingleThreadedPool().submit(this);
    }

    private static class MeterMapConsumer implements Consumer<Meter> {
        //Reuse an instance to reduce garbage churn
        static final MeterMapConsumer INSTANCE = new MeterMapConsumer(null);

        private List<WildcardMatcher> disabledMetrics;

        public MeterMapConsumer(List<WildcardMatcher> disabledMetrics) {
            this.disabledMetrics = disabledMetrics;
        }

        public MeterMapConsumer reset(List<WildcardMatcher> disabledMetrics2){
            disabledMetrics = disabledMetrics2;
            meters.clear();
            return this;
        }

        final Map<Meter.Id, Meter> meters = new HashMap<>();

        @Override
        public void accept(Meter meter) {
            Meter.Id meterId = meter.getId();
            if (WildcardMatcher.isNoneMatch(disabledMetrics, meterId.getName())) {
                meters.put(meterId, meter);
            }
        }
    }

    public void addConfig(final MeterRegistry meterRegistry, final SimpleConfig config) {
        if (configMap.putIfAbsent(meterRegistry, config) != null) {
            return;
        }
        logger.debug("Identified Micrometer SimpleConfig: {}", config);

        //this next only happens during testing
        //can't use instanceof or casts because of different classloaders
        if (config.getClass().getName().equals("co.elastic.apm.agent.micrometer.MicrometerMetricsReporter$OneSecondStepSimpleConfig")) {
            meterRegistry.counter("MicrometerMetricsReporter_OneSecondStepSimpleConfig").increment(config.hashCode());
        }
    }

    static class Step {
        long lastStepCount = 0;
        long stepInMs;

        public Step(long step) {
            stepInMs = step;
        }

        public boolean isStep() {
            return stepInMs > 0;
        }

        public boolean shouldReportNow(long now) {
            //I tried many implementations, relative time based and incremental time based
            //this one aligned to the micrometer steps seems to work best
            long newStepCount = now / stepInMs;
            return newStepCount > lastStepCount;
        }

        public void incrementToNextStep(long now) {
            lastStepCount = now / stepInMs;
        }
    }

    //for testing only
    WeakMap<MeterRegistry, Step> getMeterRegistries() {
        return meterRegistries;
    }

    //for testing only
    Iterable<Meter> getFailedMeters() {
        return serializer.getFailedMeters();
    }

    //for testing only
    void resetNow(long now) {
        lastMetricIntervalTimestamp = now;
    }

    //for testing only
    //default SimpleMeterRegistry step is 1 minute (so would need to wait that long to see the metric!)
    //This has a 1 second step and `meterRegistry` is set when config instrumentation matches
    static class OneSecondStepSimpleConfig implements SimpleConfig{

        @Override
        public CountingMode mode() {
            return CountingMode.STEP;
        }

        @Override
        public Duration step() {
            return Duration.ofSeconds(1);
        }

        @Override
        public String get(String key) {
            return null;
        }
    }
}
