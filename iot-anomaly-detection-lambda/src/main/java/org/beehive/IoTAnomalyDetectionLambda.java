// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
package org.beehive;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.beehive.domain.AnomalyResult;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import software.amazon.cloudwatchlogs.emf.model.Unit;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.metrics.MetricsUtils;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.beehive.RandomCutForestService.getAnomalyScores;


public class IoTAnomalyDetectionLambda implements RequestHandler<Map<String, String>, List<AnomalyResult>> {
    private static final String HIVE_EVENTS = "HIVE_EVENTS";
    private static final Logger LOG = LogManager.getLogger();

    AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.standard().build();

    static MetricsLogger metricsLogger = MetricsUtils.metricsLogger();

    public record EventsToProcess(String datetime, Double weight){}

    @Override
    @Metrics(namespace = "BeeHive", service = "IoTAnomalyDetectionLambda")
    @Tracing
    @Logging(logEvent = true)
    public List<AnomalyResult> handleRequest(Map<String, String> parameters, Context context) {

        String hiveID = parameters.get("hiveID");

        LOG.info("Received request for hiveID : {}", hiveID);

        List<EventsToProcess> eventsToProcesses = findPreviousEventsForHive(hiveID);
        List<AnomalyResult> anomalyResults = getAnomalyScores(eventsToProcesses);

        for (AnomalyResult anomalyResult : anomalyResults) {

            updateHiveEventToDynamoDatabase(anomalyResult, hiveID);
            if(anomalyResult.isEventAnomalous()) {
                LOG.warn("Anomaly detected for date_time {}", anomalyResult.datetime());
                metricsLogger.putMetric("Anomaly detected", 1, Unit.COUNT);
            } else {
                LOG.info("No anomaly detected for date_time {}", anomalyResult.datetime());
                metricsLogger.putMetric("No anomaly detected", 1, Unit.COUNT);
            }
        }

        return anomalyResults.stream().filter(AnomalyResult::isEventAnomalous).toList();
    }

    @Tracing
    private void updateHiveEventToDynamoDatabase(AnomalyResult anomalyResult, String hiveID) {
        Map<String, AttributeValue> attributesMap = new HashMap<>();
        attributesMap.put("hive_id", new AttributeValue(hiveID));
        attributesMap.put("date_time", new AttributeValue(anomalyResult.datetime()));
        attributesMap.put("weight", new AttributeValue(String.valueOf(anomalyResult.weight())));
        attributesMap.put("expected_weight", new AttributeValue(String.valueOf(anomalyResult.expectedValue())));
        attributesMap.put("anomaly_score", new AttributeValue(String.valueOf(anomalyResult.anomalyScore())));
        attributesMap.put("is_event_anomalous", new AttributeValue(String.valueOf(anomalyResult.isEventAnomalous())));
        dynamoDBClient.putItem(HIVE_EVENTS, attributesMap);
    }

    @Tracing
    List<EventsToProcess> findPreviousEventsForHive(String hiveID) {
        Map<String, String> expressionAttributesNames = new HashMap<>();
        expressionAttributesNames.put("#hive_id", "hive_id");

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":hive_id", new AttributeValue().withS(hiveID));

        QueryRequest queryRequest = new QueryRequest()
                .withTableName(HIVE_EVENTS)
                .withKeyConditionExpression("#hive_id = :hive_id")
                .withExpressionAttributeNames(expressionAttributesNames)
                .withExpressionAttributeValues(expressionAttributeValues);

        QueryResult queryResult = dynamoDBClient.query(queryRequest);

        List<Map<String, AttributeValue>> items = queryResult.getItems();

        return  items.stream()
                                   .map(m -> new EventsToProcess(
                                           m.get("date_time").getS(),
                                           Double.valueOf((m.get("weight")).getS())))
                                   .toList();
    }
}
