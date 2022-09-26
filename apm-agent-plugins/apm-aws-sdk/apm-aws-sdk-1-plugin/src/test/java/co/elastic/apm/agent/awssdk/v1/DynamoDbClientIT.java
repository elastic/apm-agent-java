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

import co.elastic.apm.agent.awssdk.common.AbstractAwsClientIT;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;


public class DynamoDbClientIT extends AbstractAwsClientIT {

    private AmazonDynamoDB dynamoDB;
    private AmazonDynamoDBAsync dynamoDBAsync;

    private final Consumer<Span> dbAssert = span -> assertThat(span.getContext().getDb().getInstance()).isEqualTo(localstack.getRegion());


    @BeforeEach
    public void setupClient() {
        dynamoDB = AmazonDynamoDBClient.builder()
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString(), localstack.getRegion()))
            .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(localstack.getAccessKey(), localstack.getSecretKey())))
            .build();
        dynamoDBAsync = AmazonDynamoDBAsyncClient.asyncBuilder()
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString(), localstack.getRegion()))
            .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(localstack.getAccessKey(), localstack.getSecretKey())))
            .build();
    }

    @Test
    public void testDynamoDbClient() {
        Transaction transaction = startTestRootTransaction("s3-test");

        executeTest("CreateTable", "query", TABLE_NAME, () -> dynamoDB.createTable(new CreateTableRequest().withTableName(TABLE_NAME)
                .withAttributeDefinitions(List.of(
                    new AttributeDefinition("attributeOne", ScalarAttributeType.S),
                    new AttributeDefinition("attributeTwo", ScalarAttributeType.N)
                ))
                .withKeySchema(List.of(
                    new KeySchemaElement("attributeOne", KeyType.HASH),
                    new KeySchemaElement("attributeTwo", KeyType.RANGE)
                ))
                .withBillingMode(BillingMode.PAY_PER_REQUEST)),
            dbAssert);

        executeTest("ListTables", "query", null, () -> dynamoDB.listTables(),
            dbAssert);

        executeTest("PutItem", "query", TABLE_NAME, () -> dynamoDB.putItem(
            new PutItemRequest(TABLE_NAME,
                Map.of("attributeOne", new AttributeValue("valueOne"), "attributeTwo", new AttributeValue().withN("10")))),
            dbAssert);

        executeTest("Query", "query", TABLE_NAME, () -> dynamoDB.query(
            new QueryRequest(TABLE_NAME)
                .withKeyConditionExpression(KEY_CONDITION_EXPRESSION)
                .withExpressionAttributeValues(Map.of(":one", new AttributeValue("valueOne")))),
            dbAssert
                .andThen(span -> assertThat(span.getContext().getDb()).hasStatement(KEY_CONDITION_EXPRESSION)));

        executeTest("DeleteTable", "query", TABLE_NAME, () -> dynamoDB.deleteTable(TABLE_NAME),
            dbAssert);

        executeTestWithException(ResourceNotFoundException.class, "PutItem", "query", TABLE_NAME + "-exception", () -> dynamoDB.putItem(
            new PutItemRequest(TABLE_NAME + "-exception",
                Map.of("attributeOne", new AttributeValue("valueOne"), "attributeTwo", new AttributeValue().withN("10")))),
            dbAssert);

        assertThat(reporter.getSpans().size()).isEqualTo(6);

        transaction.deactivate().end();

        assertThat(reporter.getNumReportedErrors()).isEqualTo(1);
        assertThat(reporter.getFirstError().getException()).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    public void testDynamoDbClientAsync() {
        Transaction transaction = startTestRootTransaction("s3-test");

        executeTest("CreateTable", "query", TABLE_NAME, () -> dynamoDBAsync.createTableAsync(new CreateTableRequest().withTableName(TABLE_NAME)
                .withAttributeDefinitions(List.of(
                    new AttributeDefinition("attributeOne", ScalarAttributeType.S),
                    new AttributeDefinition("attributeTwo", ScalarAttributeType.N)
                ))
                .withKeySchema(List.of(
                    new KeySchemaElement("attributeOne", KeyType.HASH),
                    new KeySchemaElement("attributeTwo", KeyType.RANGE)
                ))
                .withBillingMode(BillingMode.PAY_PER_REQUEST)),
            dbAssert);

        executeTest("ListTables", "query", null, () -> dynamoDBAsync.listTablesAsync(),
            dbAssert);

        executeTest("PutItem", "query", TABLE_NAME, () -> dynamoDBAsync.putItemAsync(
            new PutItemRequest(TABLE_NAME,
                Map.of("attributeOne", new AttributeValue("valueOne"), "attributeTwo", new AttributeValue().withN("10")))),
            dbAssert);

        executeTest("Query", "query", TABLE_NAME, () -> dynamoDBAsync.queryAsync(
            new QueryRequest(TABLE_NAME)
                .withKeyConditionExpression(KEY_CONDITION_EXPRESSION)
                .withExpressionAttributeValues(Map.of(":one", new AttributeValue("valueOne")))),
            dbAssert
                .andThen(span -> assertThat(span.getContext().getDb()).hasStatement(KEY_CONDITION_EXPRESSION)));

        executeTest("DeleteTable", "query", TABLE_NAME, () -> dynamoDBAsync.deleteTableAsync(TABLE_NAME),
            dbAssert);

        assertThat(reporter.getSpans().size()).isEqualTo(5);

        transaction.deactivate().end();

        assertThat(reporter.getSpans()).noneMatch(AbstractSpan::isSync);
    }

    @Override
    protected String awsService() {
        return "DynamoDB";
    }

    @Override
    protected String type() {
        return "db";
    }

    @Override
    protected LocalStackContainer.Service localstackService() {
        return LocalStackContainer.Service.DYNAMODB;
    }

}
