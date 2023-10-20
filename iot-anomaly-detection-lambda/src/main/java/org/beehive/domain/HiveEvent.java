package org.beehive.domain;
public class HiveEvent {
    String eventId;
    Integer hiveID;
    String datetime;
    Double weight;

    public String getEventId() {
        return eventId;
    }

    public Integer getHiveID() {
        return hiveID;
    }

    public String getDatetime() {
        return datetime;
    }

    public Double getWeight() {
        return weight;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
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
