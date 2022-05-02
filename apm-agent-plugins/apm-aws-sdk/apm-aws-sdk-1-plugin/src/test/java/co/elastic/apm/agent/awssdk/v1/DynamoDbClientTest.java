package co.elastic.apm.agent.awssdk.v1;

import co.elastic.apm.agent.awssdk.common.AbstractAwsClientTest;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
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
import com.amazonaws.services.s3.model.AmazonS3Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


public class DynamoDbClientTest extends AbstractAwsClientTest {

    private AmazonDynamoDB dynamoDB;
    private AmazonDynamoDBAsync dynamoDBAsync;

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
                new KeySchemaElement("attributeOne",KeyType.HASH),
                new KeySchemaElement("attributeTwo",KeyType.RANGE)
            ))
            .withBillingMode(BillingMode.PAY_PER_REQUEST)));

        executeTest("ListTables", "query", null, () -> dynamoDB.listTables());

        executeTest("PutItem", "query", TABLE_NAME, () -> dynamoDB.putItem(
            new PutItemRequest(TABLE_NAME,
                Map.of("attributeOne", new AttributeValue("valueOne"), "attributeTwo", new AttributeValue().withN("10")))));

        executeTest("Query", "query", TABLE_NAME, () -> dynamoDB.query(
            new QueryRequest(TABLE_NAME)
                .withKeyConditionExpression(KEY_CONDITION_EXPRESSION)
                .withExpressionAttributeValues(Map.of(":one", new AttributeValue("valueOne")))));
        Span span = reporter.getSpanByName("DynamoDB Query " + TABLE_NAME);
        assertThat(span.getContext().getDb().getStatement()).isEqualTo(KEY_CONDITION_EXPRESSION);

        executeTest("DeleteTable", "query", TABLE_NAME, () -> dynamoDB.deleteTable(TABLE_NAME));

        executeTestWithException(ResourceNotFoundException.class,"PutItem", "query",TABLE_NAME + "-exception", () -> dynamoDB.putItem(
            new PutItemRequest(TABLE_NAME + "-exception",
                Map.of("attributeOne", new AttributeValue("valueOne"), "attributeTwo", new AttributeValue().withN("10")))));

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
                new KeySchemaElement("attributeOne",KeyType.HASH),
                new KeySchemaElement("attributeTwo",KeyType.RANGE)
            ))
            .withBillingMode(BillingMode.PAY_PER_REQUEST)));

        executeTest("ListTables", "query", null, () -> dynamoDBAsync.listTablesAsync());

        executeTest("PutItem", "query", TABLE_NAME, () -> dynamoDBAsync.putItemAsync(
            new PutItemRequest(TABLE_NAME,
                Map.of("attributeOne", new AttributeValue("valueOne"), "attributeTwo", new AttributeValue().withN("10")))));

        executeTest("Query", "query", TABLE_NAME, () -> dynamoDBAsync.queryAsync(
            new QueryRequest(TABLE_NAME)
                .withKeyConditionExpression(KEY_CONDITION_EXPRESSION)
                .withExpressionAttributeValues(Map.of(":one", new AttributeValue("valueOne")))));
        Span span = reporter.getSpanByName("DynamoDB Query " + TABLE_NAME);
        assertThat(span.getContext().getDb().getStatement()).isEqualTo(KEY_CONDITION_EXPRESSION);

        executeTest("DeleteTable", "query", TABLE_NAME, () -> dynamoDBAsync.deleteTableAsync(TABLE_NAME));

        executeTestWithException(ResourceNotFoundException.class,"PutItem", "query",TABLE_NAME + "-exception", () -> dynamoDBAsync.putItem(
            new PutItemRequest(TABLE_NAME + "-exception",
                Map.of("attributeOne", new AttributeValue("valueOne"), "attributeTwo", new AttributeValue().withN("10")))));

        assertThat(reporter.getSpans().size()).isEqualTo(6);

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
