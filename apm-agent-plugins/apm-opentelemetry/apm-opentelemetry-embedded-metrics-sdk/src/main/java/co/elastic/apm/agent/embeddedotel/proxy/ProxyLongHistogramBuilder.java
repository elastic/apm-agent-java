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
import io.opentelemetry.api.metrics.LongHistogramBuilder;

import java.util.ArrayList;
import java.util.List;

public class ProxyLongHistogramBuilder {

    private final LongHistogramBuilder delegate;

    public ProxyLongHistogramBuilder(LongHistogramBuilder delegate) {
        this.delegate = delegate;
        //apply default bucket boundaries, they are guaranteed to be ordered
        List<Double> boundaries = GlobalTracer.get().getConfig(MetricsConfiguration.class).getCustomMetricsHistogramBoundaries();
        delegate.setExplicitBucketBoundariesAdvice(convertToLongBoundaries(boundaries));
    }

    private List<Long> convertToLongBoundaries(List<Double> boundaries) {
        List<Long> result = new ArrayList<>();
        for(double val : boundaries) {
            long rounded = Math.round(val);
            //Do not add the same boundary twice
            if(rounded > 0 && (result.isEmpty() || result.get(result.size() - 1) != rounded)) {
                result.add(rounded);
            }
        }
        return result;
    }

    public LongHistogramBuilder getDelegate() {
        return delegate;
    }

    public ProxyLongHistogram build() {
        return new ProxyLongHistogram(delegate.build());
    }

    public ProxyLongHistogramBuilder setUnit(String arg0) {
        delegate.setUnit(arg0);
        return this;
    }

    public ProxyLongHistogramBuilder setDescription(String arg0) {
        delegate.setDescription(arg0);
        return this;
    }

    public ProxyLongHistogramBuilder setExplicitBucketBoundariesAdvice(List<Long> bucketBoundaries) {
        delegate.setExplicitBucketBoundariesAdvice(bucketBoundaries);
        return this;
    }
}
