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
package co.elastic.apm.agent.awssdk.v1.helper;

import co.elastic.apm.agent.awssdk.common.AbstractS3InstrumentationHelper;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Tracer;
import com.amazonaws.Request;
import com.amazonaws.http.ExecutionContext;

import javax.annotation.Nullable;

public class S3Helper extends AbstractS3InstrumentationHelper<Request<?>, ExecutionContext> {

    private static final S3Helper INSTANCE = new S3Helper(GlobalTracer.get());

    public static S3Helper getInstance() {
        return INSTANCE;
    }

    public S3Helper(Tracer tracer) {
        super(tracer, SdkV1DataSource.getInstance());
    }

    @Nullable
    @Override
    protected String getObjectKey(Request<?> request, @Nullable String bucketName) {
        if (bucketName == null) {
            return null;
        }
        String resourcePath = request.getResourcePath();
        if (!resourcePath.startsWith(bucketName)) {
            return null;
        }
        int start = bucketName.length();
        if (start < resourcePath.length() && resourcePath.charAt(start) == '/') {
            start++;
        }
        int end = resourcePath.length();
        if (start < end) {
            return resourcePath.substring(start, end);
        }
        return null;
    }

    @Nullable
    @Override
    protected String getCopySource(Request<?> request) {
        String header = request.getHeaders().get("x-amz-copy-source");
        if (header != null && header.startsWith("/")) {
            header = header.substring(1);
        }
        return header;

    }
}
