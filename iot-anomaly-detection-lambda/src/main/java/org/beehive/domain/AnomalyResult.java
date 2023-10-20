package org.beehive.domain;

public record AnomalyResult(String datetime, Double weight, double anomalyGrade, double anomalyScore, double expectedValue, boolean isEventAnomalous) {
}
