package community.mlops;

import community.mlops.models.Metrics;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class MetricsSerde implements Serde<Metrics> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
    }

    @Override
    public void close() {
    }

    @Override
    public Serializer<Metrics> serializer() {
        return new Serializer<Metrics>() {
            @Override
            public void configure(Map<String, ?> configs, boolean isKey) {
            }

            @Override
            public byte[] serialize(String topic, Metrics data) {
                String metricsData = data.getCount() + ":" + data.getSum() + ":" + data.getAvgTime();
                return metricsData.getBytes();
            }

            @Override
            public void close() {
            }
        };
    }


    @Override
    public Deserializer<Metrics> deserializer() {
        return new Deserializer<Metrics>() {
            @Override
            public void configure(Map<String, ?> configs, boolean isKey) {
            }

            @Override
            public Metrics deserialize(String topic, byte[] dataBytes) {
                String dataStr = new String(dataBytes);
                Long count = Long.valueOf(dataStr.split(":")[0]);
                Long sum = Long.valueOf(dataStr.split(":")[1]);
                Double avgTime = Double.valueOf(dataStr.split(":")[2]);
                return new Metrics(count, sum, avgTime);
            }

            @Override
            public void close() {
            }
        };
    }
}
