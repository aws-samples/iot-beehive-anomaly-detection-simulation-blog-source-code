package org.beehive;
public class HiveEvent {
    final String hiveID;
    final String datetime;
    final String weight;

    private HiveEvent(Builder builder) {
        hiveID = builder.hiveID;
        datetime = builder.dateTime;
        weight = builder.weight;
    }

    public String getHiveID() {
        return hiveID;
    }

    public String getDatetime() {
        return datetime;
    }

    public String getWeight() {
        return weight;
    }

    /*
    Example
        {
                "hiveID"  : "1",
                "datetime" : "2023-05-01 00:00:00.0 +0200",
                "weight" : "65028"
        }
    */

    @Override
    public String toString() {
        String template =  """
            {
            "hiveID": %s,
            "datetime":"%s",
            "weight": %s
            }
        """;
        return String.format(template,hiveID, datetime,weight);
    }

    public static final class Builder {
        private String hiveID;
        private String dateTime;
        private String weight;

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder hiveID(String val) {
            hiveID = val;
            return this;
        }

        public Builder dateTime(String val) {
            dateTime = val;
            return this;
        }

        public Builder weight(String val) {
            weight = val;
            return this;
        }

        public HiveEvent build() {
            return new HiveEvent(this);
        }
    }


}
