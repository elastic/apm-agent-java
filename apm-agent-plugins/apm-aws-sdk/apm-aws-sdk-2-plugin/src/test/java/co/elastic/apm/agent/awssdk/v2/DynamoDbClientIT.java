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
package co.elastic.apm.agent.awssdk.v2;

import co.elastic.apm.agent.awssdk.common.AbstractAwsClientIT;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;


public class DynamoDbClientIT extends AbstractAwsClientIT {

    private DynamoDbClient dynamoDB;
    private DynamoDbAsyncClient dynamoDBAsync;

    private Consumer<Span> dbAssert = span -> assertThat(span).hasDbInstance(localstack.getRegion());

    private static final Map<String, AttributeValue> ITEM = Stream.of(
        new AbstractMap.SimpleEntry<>("attributeOne", AttributeValue.builder().s("valueOne").build()),
        new AbstractMap.SimpleEntry<>("attributeTwo", AttributeValue.builder().n("10").build()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final Map<String, AttributeValue> EXPRESSION_ATTRIBUTE_VALUES = Collections.singletonMap(":one", AttributeValue.builder().s("valueOne").build());

    @BeforeEach
    public void setupClient() {
        dynamoDB = DynamoDbClient.builder().endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                localstack.getAccessKey(), localstack.getSecretKey()
            )))
            .region(Region.of(localstack.getRegion())).build();

        dynamoDBAsync = DynamoDbAsyncClient.builder().endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                localstack.getAccessKey(), localstack.getSecretKey()
            )))
            .region(Region.of(localstack.getRegion())).build();
    }

    @Test
    public void testDynamoDbClient() {
        Transaction transaction = startTestRootTransaction("s3-test");

        executeTest("CreateTable", "query", TABLE_NAME, () -> dynamoDB.createTable(CreateTableRequest.builder()
                .tableName(TABLE_NAME)
                .attributeDefinitions(Arrays.asList(
                    AttributeDefinition.builder().attributeName("attributeOne").attributeType(ScalarAttributeType.S).build(),
                    AttributeDefinition.builder().attributeName("attributeTwo").attributeType(ScalarAttributeType.N).build()
                ))
                .keySchema(Arrays.asList(
                    KeySchemaElement.builder().attributeName("attributeOne").keyType(KeyType.HASH).build(),
                    KeySchemaElement.builder().attributeName("attributeTwo").keyType(KeyType.RANGE).build()
                ))
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build()),
            dbAssert);

        executeTest("ListTables", "query", null, () -> dynamoDB.listTables(), dbAssert);

        executeTest("PutItem", "query", TABLE_NAME, () -> dynamoDB.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(ITEM)
                .build()),
            dbAssert);

        executeTest("Query", "query", TABLE_NAME, () -> dynamoDB.query(QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression(KEY_CONDITION_EXPRESSION)
                .expressionAttributeValues(EXPRESSION_ATTRIBUTE_VALUES)
                .build()),
            dbAssert);
        Span span = reporter.getSpanByName("DynamoDB Query " + TABLE_NAME);
        assertThat(span).hasDbStatement(KEY_CONDITION_EXPRESSION);

        executeTest("DeleteTable", "query", TABLE_NAME, () -> dynamoDB.deleteTable(DeleteTableRequest.builder().tableName(TABLE_NAME).build()), dbAssert);

        executeTestWithException(ResourceNotFoundException.class, "PutItem", "query", TABLE_NAME + "exception", () -> dynamoDB.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME + "exception")
                .item(ITEM)
                .build()),
            dbAssert);

        assertThat(reporter.getSpans().size()).isEqualTo(6);
        assertThat(reporter.getSpans()).allMatch(AbstractSpan::isSync);

        transaction.deactivate().end();

        assertThat(reporter.getNumReportedErrors()).isEqualTo(1);
        assertThat(reporter.getFirstError().getException()).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    public void testDynamoDbClientAsync() {
        Transaction transaction = startTestRootTransaction("s3-test");

        executeTest("CreateTable", "query", TABLE_NAME, () -> dynamoDBAsync.createTable(CreateTableRequest.builder()
                .tableName(TABLE_NAME)
                .attributeDefinitions(Arrays.asList(
                    AttributeDefinition.builder().attributeName("attributeOne").attributeType(ScalarAttributeType.S).build(),
                    AttributeDefinition.builder().attributeName("attributeTwo").attributeType(ScalarAttributeType.N).build()
                ))
                .keySchema(Arrays.asList(
                    KeySchemaElement.builder().attributeName("attributeOne").keyType(KeyType.HASH).build(),
                    KeySchemaElement.builder().attributeName("attributeTwo").keyType(KeyType.RANGE).build()
                ))
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build()),
            dbAssert);

        executeTest("ListTables", "query", null, () -> dynamoDBAsync.listTables());

        executeTest("PutItem", "query", TABLE_NAME, () -> dynamoDBAsync.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(ITEM)
                .build()),
            dbAssert);

        executeTest("Query", "query", TABLE_NAME, () -> dynamoDBAsync.query(QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression(KEY_CONDITION_EXPRESSION)
                .expressionAttributeValues(EXPRESSION_ATTRIBUTE_VALUES)
                .build()),
            dbAssert);
        Span span = reporter.getSpanByName("DynamoDB Query " + TABLE_NAME);
        assertThat(span).hasDbStatement(KEY_CONDITION_EXPRESSION);

        executeTest("DeleteTable", "query", TABLE_NAME, () -> dynamoDBAsync.deleteTable(DeleteTableRequest.builder().tableName(TABLE_NAME).build()),
            dbAssert);

        executeTestWithException(CompletionException.class, "PutItem", "query", TABLE_NAME + "exception", () -> dynamoDBAsync.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME + "exception")
                .item(ITEM)
                .build()),
            dbAssert);

        assertThat(reporter.getSpans().size()).isEqualTo(6);
        assertThat(reporter.getSpans()).allMatch(s -> !s.isSync());

        transaction.deactivate().end();

        assertThat(reporter.getNumReportedErrors()).isEqualTo(1);
        assertThat(reporter.getFirstError().getException()).isInstanceOf(ResourceNotFoundException.class);
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
