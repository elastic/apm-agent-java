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
package co.elastic.apm.agent.embeddedotel.proxy;

import co.elastic.apm.agent.configuration.MetricsConfiguration;
import co.elastic.apm.agent.tracer.GlobalTracer;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;

import java.util.List;

public class ProxyDoubleHistogramBuilder {

    private final DoubleHistogramBuilder delegate;

    public ProxyDoubleHistogramBuilder(DoubleHistogramBuilder delegate) {
        this.delegate = delegate;
        //apply default bucket boundaries
        List<Double> boundaries = GlobalTracer.get().getConfig(MetricsConfiguration.class).getCustomMetricsHistogramBoundaries();
        delegate.setExplicitBucketBoundariesAdvice(boundaries);
    }

    public DoubleHistogramBuilder getDelegate() {
        return delegate;
    }

    public ProxyDoubleHistogram build() {
        return new ProxyDoubleHistogram(delegate.build());
    }

    public ProxyLongHistogramBuilder ofLongs() {
        return new ProxyLongHistogramBuilder(delegate.ofLongs());
    }

    public ProxyDoubleHistogramBuilder setUnit(String arg0) {
        delegate.setUnit(arg0);
        return this;
    }

    public ProxyDoubleHistogramBuilder setDescription(String arg0) {
        delegate.setDescription(arg0);
        return this;
    }

    public ProxyDoubleHistogramBuilder setExplicitBucketBoundariesAdvice(List<Double> bucketBoundaries) {
        delegate.setExplicitBucketBoundariesAdvice(bucketBoundaries);
        return this;
    }
}
