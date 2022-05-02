package co.elastic.apm.agent.awssdk.v2;

import co.elastic.apm.agent.awssdk.common.AbstractAwsClientTest;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;


public class DynamoDbClientTest extends AbstractAwsClientTest {

    private DynamoDbClient dynamoDB;
    private DynamoDbAsyncClient dynamoDBAsync;

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
            .attributeDefinitions(List.of(
                AttributeDefinition.builder().attributeName("attributeOne").attributeType(ScalarAttributeType.S).build(),
                AttributeDefinition.builder().attributeName("attributeTwo").attributeType(ScalarAttributeType.N).build()
            ))
            .keySchema(List.of(
                KeySchemaElement.builder().attributeName("attributeOne").keyType(KeyType.HASH).build(),
                KeySchemaElement.builder().attributeName("attributeTwo").keyType(KeyType.RANGE).build()
            ))
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build()));

        executeTest("ListTables", "query", null, () -> dynamoDB.listTables());

        executeTest("PutItem", "query", TABLE_NAME, () -> dynamoDB.putItem(PutItemRequest.builder()
            .tableName(TABLE_NAME)
            .item(Map.of("attributeOne", AttributeValue.builder().s("valueOne").build(), "attributeTwo", AttributeValue.builder().n("10").build()))
            .build()));

        executeTest("Query", "query", TABLE_NAME, () -> dynamoDB.query(QueryRequest.builder()
            .tableName(TABLE_NAME)
            .keyConditionExpression(KEY_CONDITION_EXPRESSION)
            .expressionAttributeValues(Map.of(":one", AttributeValue.builder().s("valueOne").build()))
            .build()));
        Span span = reporter.getSpanByName("DynamoDB Query " + TABLE_NAME);
        assertThat(span.getContext().getDb().getStatement()).isEqualTo(KEY_CONDITION_EXPRESSION);

        executeTest("DeleteTable", "query", TABLE_NAME, () -> dynamoDB.deleteTable(DeleteTableRequest.builder().tableName(TABLE_NAME).build()));

        executeTestWithException(ResourceNotFoundException.class, "PutItem", "query", TABLE_NAME + "exception", () -> dynamoDB.putItem(PutItemRequest.builder()
            .tableName(TABLE_NAME + "exception")
            .item(Map.of("attributeOne", AttributeValue.builder().s("valueOne").build(), "attributeTwo", AttributeValue.builder().n("10").build()))
            .build()));

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
            .attributeDefinitions(List.of(
                AttributeDefinition.builder().attributeName("attributeOne").attributeType(ScalarAttributeType.S).build(),
                AttributeDefinition.builder().attributeName("attributeTwo").attributeType(ScalarAttributeType.N).build()
            ))
            .keySchema(List.of(
                KeySchemaElement.builder().attributeName("attributeOne").keyType(KeyType.HASH).build(),
                KeySchemaElement.builder().attributeName("attributeTwo").keyType(KeyType.RANGE).build()
            ))
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build()));

        executeTest("ListTables", "query", null, () -> dynamoDBAsync.listTables());

        executeTest("PutItem", "query", TABLE_NAME, () -> dynamoDBAsync.putItem(PutItemRequest.builder()
            .tableName(TABLE_NAME)
            .item(Map.of("attributeOne", AttributeValue.builder().s("valueOne").build(), "attributeTwo", AttributeValue.builder().n("10").build()))
            .build()));

        executeTest("Query", "query", TABLE_NAME, () -> dynamoDBAsync.query(QueryRequest.builder()
            .tableName(TABLE_NAME)
            .keyConditionExpression(KEY_CONDITION_EXPRESSION)
            .expressionAttributeValues(Map.of(":one", AttributeValue.builder().s("valueOne").build()))
            .build()));
        Span span = reporter.getSpanByName("DynamoDB Query " + TABLE_NAME);
        assertThat(span.getContext().getDb().getStatement()).isEqualTo(KEY_CONDITION_EXPRESSION);

        executeTest("DeleteTable", "query", TABLE_NAME, () -> dynamoDBAsync.deleteTable(DeleteTableRequest.builder().tableName(TABLE_NAME).build()));

        executeTestWithException(CompletionException.class, "PutItem", "query", TABLE_NAME + "exception", () -> dynamoDBAsync.putItem(PutItemRequest.builder()
            .tableName(TABLE_NAME + "exception")
            .item(Map.of("attributeOne", AttributeValue.builder().s("valueOne").build(), "attributeTwo", AttributeValue.builder().n("10").build()))
            .build()));

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
