package org.beehive.domain;
public class HiveEvent {
    Integer hiveID;
    String datetime;
    Double weight;

    public Integer getHiveID() {
        return hiveID;
    }

    public String getDatetime() {
        return datetime;
    }

    public Double getWeight() {
        return weight;
    }

    public void setHiveID(Integer hiveID) {
        this.hiveID = hiveID;
    }

    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }
}
