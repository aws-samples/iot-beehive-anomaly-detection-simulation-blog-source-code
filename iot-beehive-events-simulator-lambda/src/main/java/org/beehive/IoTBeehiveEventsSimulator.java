package org.beehive;


import com.amazonaws.services.iotdata.AWSIotData;
import com.amazonaws.services.iotdata.AWSIotDataAsyncClientBuilder;
import com.amazonaws.services.iotdata.model.PublishRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.common.collect.Streams;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class IoTBeehiveEventsSimulator implements RequestHandler<Map<String, String>, List<HiveEvent>> {

    private static final Logger LOG = LogManager.getLogger();

    @Override
    @Metrics(namespace = "BeeHive", service = "anomalydetection")
    @Tracing
    @Logging(logEvent = true)
    public List<HiveEvent> handleRequest(Map<String, String> parameters, Context context) {

        String hiveID = parameters.get("hiveID");

        LOG.info("Received request for hiveID : {}", hiveID);

        InputStream resourceAsStream = IoTBeehiveEventsSimulator.class.getClassLoader().getResourceAsStream("hive-sample-events.json");
        DocumentContext documentContext = JsonPath.parse(resourceAsStream);

        List<String> times = documentContext.read("$..datetime");
        List weights = documentContext.read("$..weight");

        List<HiveEvent> hiveEvents = Streams.zip(times.stream(), weights.stream(), (time, weight) ->
                HiveEvent.Builder.builder()
                        .hiveID(hiveID)
                        .weight(weight.toString())
                        .dateTime(time)
                        .build())
                        .toList();

        AWSIotData iotClient = AWSIotDataAsyncClientBuilder.defaultClient();

        for (HiveEvent hiveEvent : hiveEvents) {
            PublishRequest publishRequest = new PublishRequest()
                    .withQos(1)
                    .withTopic("iot/beehive")
                    .withPayload(ByteBuffer.wrap(hiveEvent.toString().getBytes(StandardCharsets.UTF_8)));
            iotClient.publish(publishRequest);
        }

        return hiveEvents;

    }
}
