// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
/**
 * The RandomCutForestService class provides methods for detecting anomalies in a list of events
 * using the Random Cut Forest algorithm.
 *
 * This class utilizes the Amazon Random Cut Forest library to create and manage the forest
 * and perform anomaly detection on the input data.
 * https://github.com/aws/random-cut-forest-by-aws
 *
 */
package org.beehive;

import com.amazon.randomcutforest.RandomCutForest;
import com.amazon.randomcutforest.parkservices.AnomalyDescriptor;
import com.amazon.randomcutforest.parkservices.ThresholdedRandomCutForest;
import org.beehive.domain.AnomalyResult;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static com.amazon.randomcutforest.config.TransformMethod.NORMALIZE;

/**
 *  The principal method in RandomCutForestService, getAnomalyScores, calculates anomaly scores and grades
 *  using a random cut forest (RCF) algorithm. RCF is a machine learning algorithm capable of detecting
 *  anomalies in an unsupervised manner. This service uses a version of RCF developed by AWS
 *  https://github.com/aws/random-cut-forest-by-aws
 *
 */
public class RandomCutForestService {

    private static final int NUMBER_OF_MEASUREMENTS_PER_DAY;
    private static final int NUMBER_OF_TREES;
    private static final int DIMENSIONS;
    private static final int RANDOM_SEED;
    private static final int ANOMALY_GRADE_ANOMALOUS_EVENT_CUTOFF;
    private static final int ANOMALY_SCORE_ANOMALOUS_EVENT_CUTOFF;


    static {
        Properties properties = new Properties();
        try {
            InputStream inputStream = RandomCutForestService.class.getResourceAsStream("/algorithm.properties");
            if (inputStream != null) {
                properties.load(inputStream);
            } else {
                throw new RuntimeException("Resource 'algorithm.properties' not found.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error loading properties from resource.", e);
        }

        NUMBER_OF_MEASUREMENTS_PER_DAY = Integer.parseInt(properties.getProperty("number.of.measurements.per.day"));
        NUMBER_OF_TREES = Integer.parseInt(properties.getProperty("number.of.trees"));
        DIMENSIONS = Integer.parseInt(properties.getProperty("dimensions"));
        RANDOM_SEED = Integer.parseInt(properties.getProperty("random.seed"));
        ANOMALY_GRADE_ANOMALOUS_EVENT_CUTOFF = Integer.parseInt(properties.getProperty("anomaly.grade.anomalous.event.cutoff"));
        ANOMALY_SCORE_ANOMALOUS_EVENT_CUTOFF = Integer.parseInt(properties.getProperty("anomaly.score.anomalous.event.cutoff"));
    }


    /**
     * Taking a list of events this method will calculate the anomaly score and anomaly grade of each event.
     * @param eventsToProcesses A list of events to process
     * @return a List<AnomalyResult>
      */
    @Tracing
    public static List<AnomalyResult> getAnomalyScores(List<IoTAnomalyDetectionLambda.EventsToProcess> eventsToProcesses) {

        ThresholdedRandomCutForest thresholdedRandomCutForest = ThresholdedRandomCutForest.builder()
                .numberOfTrees(NUMBER_OF_TREES)
                .dimensions(DIMENSIONS * NUMBER_OF_MEASUREMENTS_PER_DAY)
                .internalShinglingEnabled(true)
                .shingleSize(NUMBER_OF_MEASUREMENTS_PER_DAY)
                .randomSeed(RANDOM_SEED)
                .transformMethod(NORMALIZE)
                .build();

        RandomCutForest forest = RandomCutForest.builder()
                .numberOfTrees(NUMBER_OF_TREES)
                .dimensions(DIMENSIONS * NUMBER_OF_MEASUREMENTS_PER_DAY)
                .internalShinglingEnabled(true)
                .shingleSize(NUMBER_OF_MEASUREMENTS_PER_DAY)
                .randomSeed(RANDOM_SEED)
                .centerOfMassEnabled(false)
                .build();

        double score;
        double anomalyGrade;
        double expectedValue = 0;

        List<AnomalyResult> anomalyResults = new ArrayList<>();

        for (IoTAnomalyDetectionLambda.EventsToProcess eventsToProcess : eventsToProcesses) {

            double[] point = {eventsToProcess.weight()};
            score = forest.getAnomalyScore(point);
            AnomalyDescriptor anomalyDescriptor = thresholdedRandomCutForest.process(point, 0L);
            assert (anomalyDescriptor.getRCFScore() == score);

            forest.update(point);
            anomalyGrade = anomalyDescriptor.getAnomalyGrade();
            double[][] expectedValuesArray = anomalyDescriptor.getExpectedValuesList();

            // The random forest algorithm is capable of analyzing multidimensional events
            // (e.g., temperature, humidity, weight), but in this simulation, we have only one dimension: weight.
            if (expectedValuesArray != null) {
                expectedValue = expectedValuesArray[0][0];
            }

            boolean isEventAnomalous = isEventAnomalous(anomalyGrade, score);

            AnomalyResult anomalyResult = new AnomalyResult(eventsToProcess.datetime(),
                    eventsToProcess.weight(),
                    anomalyGrade,
                    score,
                    expectedValue,
                    isEventAnomalous);
            anomalyResults.add(anomalyResult);
        }

        return anomalyResults;
    }

    /**
     * Determines if an event is anomalous or not from the combination of anomalyGrade and anomalyScore
     * @param anomalyGrade
     * @param anomalyScore
     * @return true if the event is anomalous
     */
    private static boolean isEventAnomalous(double anomalyGrade, double anomalyScore) {
        return anomalyGrade > ANOMALY_GRADE_ANOMALOUS_EVENT_CUTOFF && anomalyScore > ANOMALY_SCORE_ANOMALOUS_EVENT_CUTOFF;
    }
}