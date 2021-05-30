package community.mlops;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.json.JSONObject;

public class MyEventTimeExtractor implements TimestampExtractor {


    @Override
    public long extract(final ConsumerRecord<Object, Object> record, final long previousTimestamp) {
        // `Foo` is your own custom class, which we assume has a method that returns
        // the embedded timestamp (milliseconds since midnight, January 1, 1970 UTC).
        long timestamp = -1;
        try {
            GenericRecord value = (GenericRecord) record.value();
            timestamp = (Long) value.get("currentTs");
        }
        catch (Exception e) {
            timestamp = record.timestamp();
        }

        if (timestamp < 0) {
            // Invalid timestamp!  Attempt to estimate a new timestamp,
            // otherwise fall back to wall-clock time (processing-time).
            if (previousTimestamp >= 0) {
                return previousTimestamp;
            } else {
                return System.currentTimeMillis();
            }
        }
        return timestamp;
    }
}