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
package co.elastic.apm.agent.awslambda.helper;

import co.elastic.apm.agent.awslambda.MapTextHeaderGetter;
import co.elastic.apm.agent.sdk.internal.util.PrivilegedActionUtils;
import co.elastic.apm.agent.tracer.*;
import co.elastic.apm.agent.tracer.metadata.CloudOrigin;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerRequestEvent;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerResponseEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class ApplicationLoadBalancerRequestTransactionHelper extends AbstractAPIGatewayTransactionHelper<ApplicationLoadBalancerRequestEvent, ApplicationLoadBalancerResponseEvent> {
    @Nullable
    private static ApplicationLoadBalancerRequestTransactionHelper INSTANCE;

    private ApplicationLoadBalancerRequestTransactionHelper(Tracer tracer) {
        super(tracer);
    }

    public static ApplicationLoadBalancerRequestTransactionHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ApplicationLoadBalancerRequestTransactionHelper(GlobalTracer.get());
        }
        return INSTANCE;
    }

    @Override
    protected Transaction doStartTransaction(ApplicationLoadBalancerRequestEvent loadBalancerRequestEvent, Context lambdaContext) {
        Transaction transaction = tracer.startChildTransaction(loadBalancerRequestEvent.getHeaders(), MapTextHeaderGetter.INSTANCE, PrivilegedActionUtils.getClassLoader(loadBalancerRequestEvent.getClass()));

        if (transaction != null) {
            String host = getHost(loadBalancerRequestEvent.getHeaders());
            super.fillHttpRequestData(transaction, loadBalancerRequestEvent.getHttpMethod(), loadBalancerRequestEvent.getHeaders(), host,
                loadBalancerRequestEvent.getPath(), getQueryString(loadBalancerRequestEvent.getQueryStringParameters()), loadBalancerRequestEvent.getBody());
        }

        return transaction;
    }

    @Override
    public void captureOutputForTransaction(Transaction transaction, ApplicationLoadBalancerResponseEvent responseEvent) {
        fillHttpResponseData(transaction, responseEvent.getHeaders(), responseEvent.getStatusCode());
    }

    @Override
    protected void setTransactionTriggerData(Transaction transaction, ApplicationLoadBalancerRequestEvent loadBalancerRequestEvent) {
        transaction.withType(TRANSACTION_TYPE);
        CloudOrigin cloudOrigin = transaction.getContext().getCloudOrigin();
        cloudOrigin.withServiceName("elb");
        cloudOrigin.withProvider("aws");
        FaasTrigger faasTrigger = transaction.getFaas().getTrigger();
        faasTrigger.withType("http");
        faasTrigger.withRequestId(getHeader(loadBalancerRequestEvent, "x-amzn-trace-id"));
        LoadBalancerElbTargetGroupArnMetadata metadata = parseMetadata(loadBalancerRequestEvent);
        if (null != metadata) {
            ServiceOrigin serviceOrigin =  transaction.getContext().getServiceOrigin();
            serviceOrigin.withName(metadata.getTargetGroupName());
            serviceOrigin.withId(metadata.getTargetGroupArn());
            cloudOrigin.withAccountId(metadata.getAccountId());
            cloudOrigin.withRegion(metadata.getCloudRegion());
        }
    }

    @Nullable
    private String getHeader(@Nonnull ApplicationLoadBalancerRequestEvent loadBalancerRequestEvent,
                             @Nonnull String headerName) {
        Map<String, String> headers = loadBalancerRequestEvent.getHeaders();
        if (null == headers) {
            return null;
        }
        return headers.get(headerName);
    }

    @Nullable
    private LoadBalancerElbTargetGroupArnMetadata parseMetadata(ApplicationLoadBalancerRequestEvent event) {
        if (null == event.getRequestContext()) {
            return null;
        }
        ApplicationLoadBalancerRequestEvent.Elb elb = event.getRequestContext().getElb();
        if (null == elb) {
            return null;
        }
        String targetGroupArn = elb.getTargetGroupArn();
        if (null == targetGroupArn) {
            return null;
        }
        LoadBalancerElbTargetGroupArnMetadata metadata = new LoadBalancerElbTargetGroupArnMetadata(targetGroupArn);
        String[] arnParts = targetGroupArn.split(":");
        int arnPartsLength = arnParts.length;
        if (arnPartsLength < 4) {
            return metadata;
        }
        metadata.withCloudRegion(arnParts[3]);
        if (arnPartsLength < 5) {
            return metadata;
        }
        metadata.withAccountId(arnParts[4]);
        if (arnPartsLength < 6) {
            return metadata;
        }
        String targetGroup = arnParts[5];
        String[] targetGroupParts = targetGroup.split("/");
        if (targetGroupParts.length < 2) {
            return metadata;
        }
        return metadata.withTargetGroupName(targetGroupParts[2]);
    }

    @Override
    protected String getApiGatewayVersion() {
        throw new UnsupportedOperationException("Not supported by ELB");
    }

    @Nullable
    @Override
    protected String getHttpMethod(ApplicationLoadBalancerRequestEvent event) {
        return event.getHttpMethod();
    }

    @Nullable
    @Override
    protected String getRequestContextPath(ApplicationLoadBalancerRequestEvent event) {
        return event.getPath();
    }

    @Nullable
    @Override
    protected String getStage(ApplicationLoadBalancerRequestEvent event) {
        throw new UnsupportedOperationException("Not supported by ELB");
    }

    @Nullable
    @Override
    protected String getResourcePath(ApplicationLoadBalancerRequestEvent event) {
        return null;
    }

    @Nullable
    @Override
    String getDomainName(ApplicationLoadBalancerRequestEvent apiGatewayRequest) {
        return null;
    }
}
