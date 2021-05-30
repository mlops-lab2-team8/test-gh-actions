package community.mlops;

import community.mlops.models.Metrics;
import community.mlops.models.Query;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class MetricsApp {
    private static final String storeName = "metrics";


    public static KafkaStreams start(HostInfo hostInfo) {
        String schemaRegistryURL = System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", Application.SCHEMA_REGISTRY_URL);
        String bootstrapServers = System.getenv().getOrDefault("BOOTSTRAP_SERVERS", Application.BOOTSTRAP_SERVER);

        final String JOB_NAME = System.getenv().getOrDefault("JOB_NAME" , "metricsApp");

        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, JOB_NAME);
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, System.getenv().getOrDefault("AUTO_OFFSET_RESET_CONFIG", "earliest"));
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, GenericAvroSerde.class);
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        config.put(StreamsConfig.STATE_DIR_CONFIG, System.getenv().getOrDefault("STATE_STORE_LOCATION", "/tmp/kafka-streams"));
        config.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryURL);
        config.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, MyEventTimeExtractor.class);
        config.put(StreamsConfig.APPLICATION_SERVER_CONFIG, hostInfo.host() + ":" + hostInfo.port());


        StreamsBuilder builder = new StreamsBuilder();


        Serde<GenericRecord> genericAvroSerde = new GenericAvroSerde();
        Map<String, String> serdeConfig = Collections.singletonMap("schema.registry.url", schemaRegistryURL);

        genericAvroSerde.configure(serdeConfig, false);

//        final GenericPrimitiveAvroSerDe<Object> keyGenericAvroSerde = new GenericPrimitiveAvroSerDe<Object>();
//        keyGenericAvroSerde.configure(serdeConfig, true);


//        Builds a stream for the topic using the provided serdes
        String topic = System.getenv("SOURCE_TOPIC");
        KStream<String, GenericRecord> stream = builder.stream(topic, Consumed.with(Serdes.String(), genericAvroSerde));

        createStream(stream);

//        stream.print(Printed.toSysOut());

        KafkaStreams streams = new KafkaStreams(builder.build(), config);
//        streams.setUncaughtExceptionHandler((Thread thread, Throwable throwable) -> {
//            // here you should examine the throwable/exception and perform an appropriate action!
//        });
        streams.start();

        return streams;
    }

    static KStream<String, GenericRecord> createStream(KStream<String, GenericRecord> stream) {
        stream
                .selectKey((k, v) -> "key")
                .groupByKey()
                .windowedBy(TimeWindows.of(Duration.ofMinutes(1)).grace(Duration.ofMinutes(5)))
                .aggregate(
                        () -> new Metrics(0L, 0L, 0D),
                        (k, v, a) -> {
                            Long prevCount = a.getCount();
                            Long prevSum = a.getSum();

                            Long diff = (Long) v.get("predictionTs") - (Long) v.get("currentTs");


                            long newCount = prevCount + 1;
                            long newSum = prevSum + diff;
                            Double newAvg = newSum * 1.0 / newCount;

                            return new Metrics(newCount, newSum, newAvg);
                        },
                        Materialized.<String, Metrics, WindowStore<Bytes, byte[]>>as(storeName)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(new MetricsSerde())
                )
        ;
        return stream;
    }


    static ResponseEntity<String> getMetrics(KafkaStreams streams, Query query) throws InterruptedException {

        waitUntilStoreIsQueryable(storeName, QueryableStoreTypes.windowStore(), streams);

        ReadOnlyWindowStore<String, Metrics> windowStore = streams.store(storeName, QueryableStoreTypes.windowStore());
//
        Instant timeFrom = Instant.ofEpochMilli(Long.parseLong(query.getStartTime()));
        Instant timeTo = Instant.ofEpochMilli(Long.parseLong(query.getEndTime()));
        KeyValueIterator<Windowed<String>, Metrics> iterator = windowStore.fetchAll(timeFrom, timeTo);
        ArrayList<JSONObject> output = new ArrayList<>();


        while (iterator.hasNext()) {

            KeyValue<Windowed<String>, Metrics> next = iterator.next();
            Windowed<String> windowTimestamp = next.key;

            String startTime = windowTimestamp.window().startTime().toString();

            Long count = next.value.getCount();
            Long sum = next.value.getSum();
            Double avgTime = next.value.getAvgTime();

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("timestamp", startTime);
            jsonObject.put("count", count);
            jsonObject.put("sum", sum);
            jsonObject.put("avgTime", avgTime);
            output.add(jsonObject);
        }


        return new ResponseEntity<>(output.toString(), HttpStatus.OK);
    }

    public static <T> T waitUntilStoreIsQueryable(final String storeName, final QueryableStoreType<T> queryableStoreType, final KafkaStreams streams) throws InterruptedException {
        while (true) {
            try {
                return streams.store(storeName, queryableStoreType);
            } catch (final InvalidStateStoreException ignored) {
                // store not yet ready for querying
                Thread.sleep(50);
            }
        }
    }

}
