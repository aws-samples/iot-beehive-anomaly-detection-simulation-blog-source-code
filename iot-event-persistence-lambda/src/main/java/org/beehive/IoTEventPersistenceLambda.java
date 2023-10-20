// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
package org.beehive;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.beehive.domain.HiveEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import software.amazon.cloudwatchlogs.emf.model.Unit;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.metrics.MetricsUtils;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.util.HashMap;
import java.util.Map;

public class IoTEventPersistenceLambda implements RequestHandler<SQSEvent, Void>{
    private static final String HIVE_EVENTS = "HIVE_EVENTS";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOG = LogManager.getLogger();
    AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.standard()
            .build();

    static MetricsLogger metricsLogger = MetricsUtils.metricsLogger();

    @Override
    @Metrics(namespace = "BeeHive", service = "IoTEventPersistenceLambda")
    @Tracing
    @Logging(logEvent = true)
    public Void handleRequest(SQSEvent event, Context context) {

        LOG.info("Reading SQS messages");

        for(SQSEvent.SQSMessage msg : event.getRecords()){
            metricsLogger.putMetric("SQSMessage", 1, Unit.COUNT);
            HiveEvent hiveEvent = GSON.fromJson(msg.getBody(), HiveEvent.class);
            saveHiveEventToDynamoDatabase(hiveEvent);
        }

        LOG.info("Finished reading SQS messages");
        return null;
    }

    @Tracing
    private void saveHiveEventToDynamoDatabase(HiveEvent hiveEvent) {
        Map<String, AttributeValue> attributesMap = new HashMap<>();
        attributesMap.put("hive_id", new AttributeValue(String.valueOf(hiveEvent.getHiveID())));
        attributesMap.put("date_time", new AttributeValue(hiveEvent.getDatetime()));
        attributesMap.put("weight", new AttributeValue(String.valueOf(hiveEvent.getWeight())));

        PutItemRequest putItemRequest = new PutItemRequest()
                .withTableName(HIVE_EVENTS)
                .withItem(attributesMap)
                .withConditionExpression("attribute_not_exists(PartitionKey) AND attribute_not_exists(SortKey)");

        try {
            dynamoDBClient.putItem(putItemRequest);
        } catch ( ConditionalCheckFailedException exception) {
            LOG.warn("Hive event already exists in the database : {}", attributesMap);
        }
    }
}
