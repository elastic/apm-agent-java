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
package co.elastic.apm.agent.awssdk.v1;

import co.elastic.apm.agent.awssdk.common.AbstractAwsSdkInstrumentation;
import co.elastic.apm.agent.awssdk.v1.helper.SdkV1DataSource;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Entity names, such as SQS QueueName, S3 BucketName or DynamoDB TableName
 * are not available as fields that can be accessed in a generic way in the HttpClientInstrumentation.
 * Therefore, with this instrumentation classes we instrument the specific request objects to retrieve the entity names and store for later lookup.
 */
public abstract class EntityLookupInstrumentation extends AbstractAwsSdkInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("com.amazonaws.services.")
            .and(nameEndsWith("Request"))
            .and(super.getTypeMatcherPreFilter());
    }

    /**
     * Instruments the getTableName method.
     */
    public static class DynamoDBGetTableNameInstrumentation extends EntityLookupInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return nameContains("dynamodbv2.model").and(declaresMethod(named("getTableName")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("getTableName");
        }

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
            public static void exitGetTableName(@Advice.This Object requestObject, @Nullable @Advice.Return String tableName) {
                SdkV1DataSource.getInstance().putLookupValue(requestObject, tableName);
            }
        }
    }

    /**
     * Instruments the getQueueName method.
     */
    public static class SQSGetQueueNameInstrumentation extends EntityLookupInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return nameContains("sqs.model")
                .and(not(nameStartsWith("com.amazonaws.services.sqs.model.SendMessage")))
                .and(not(named("com.amazonaws.services.sqs.model.ReceiveMessageRequest")))
                .and(declaresMethod(named("getQueueName")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("getQueueName");
        }

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
            public static void exitGetQueueName(@Advice.This Object requestObject, @Nullable @Advice.Return String queueName) {
                SdkV1DataSource.getInstance().putLookupValue(requestObject, queueName);
            }
        }
    }

    /**
     * Instruments the getQueueUrl method.
     */
    public static class SQSGetQueueUrlInstrumentation extends EntityLookupInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return nameContains("sqs.model")
                .and(not(nameStartsWith("com.amazonaws.services.sqs.model.SendMessage")))
                .and(not(named("com.amazonaws.services.sqs.model.ReceiveMessageRequest")))
                .and(declaresMethod(named("getQueueUrl")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("getQueueUrl");
        }

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
            public static void exitGetQueueUrl(@Advice.This Object requestObject, @Nullable @Advice.Return String queueUrl) {
                String queueName = SdkV1DataSource.getInstance().getQueueNameFromQueueUrl(queueUrl);
                SdkV1DataSource.getInstance().putLookupValue(requestObject, queueName);
            }
        }
    }

    /**
     * Instruments the getBucketName method.
     */
    public static class S3GetBucketNameInstrumentation extends EntityLookupInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return nameContains("s3.model").and(declaresMethod(named("getBucketName")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("getBucketName");
        }

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
            public static void exitGetQueueUrl(@Advice.This Object requestObject, @Nullable @Advice.Return String bucketName) {
                SdkV1DataSource.getInstance().putLookupValue(requestObject, bucketName);
            }
        }
    }

    /**
     * Instruments the getDestinationBucketName method.
     */
    public static class S3GetDestinationBucketNameInstrumentation extends EntityLookupInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return nameContains("s3.model").and(declaresMethod(named("getDestinationBucketName")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("getDestinationBucketName");
        }

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
            public static void exitGetQueueUrl(@Advice.This Object requestObject, @Nullable @Advice.Return String bucketName) {
                SdkV1DataSource.getInstance().putLookupValue(requestObject, bucketName);
            }
        }
    }
}
