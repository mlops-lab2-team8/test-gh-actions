/**
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package io.confluent.kafka.connect.datagen;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import io.confluent.avro.random.generator.Generator;
import io.confluent.connect.avro.AvroData;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.header.ConnectHeaders;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatagenTask extends SourceTask {

  static final Logger log = LoggerFactory.getLogger(DatagenTask.class);

  private static final Schema DEFAULT_KEY_SCHEMA = Schema.OPTIONAL_STRING_SCHEMA;
  public static final String TASK_ID = "task.id";
  public static final String TASK_GENERATION = "task.generation";
  public static final String CURRENT_ITERATION = "current.iteration";
  public static final String RANDOM_SEED = "random.seed";


  private DatagenConnectorConfig config;
  private String topic;
  private long maxInterval;
  private int maxRecords;
  private long count = 0L;
  private String schemaFilename;
  private String schemaKeyField;
  private String schemaString;
  private Quickstart quickstart;
  private Generator generator;
  private org.apache.avro.Schema avroSchema;
  private org.apache.kafka.connect.data.Schema ksqlSchema;
  private AvroData avroData;
  private int taskId;
  private Map<String, Object> sourcePartition;
  private long taskGeneration;
  private Random random;

  protected enum Quickstart {
    CLICKSTREAM_CODES("clickstream_codes_schema.avro", "code"),
    CLICKSTREAM("clickstream_schema.avro", "ip"),
    CLICKSTREAM_USERS("clickstream_users_schema.avro", "user_id"),
    ORDERS("orders_schema.avro", "orderid"),
    RATINGS("ratings_schema.avro", "rating_id"),
    USERS("users_schema.avro", "userid"),
    ECOM("ecom_schema.avro", "CustomerID"),
    USERS_("users_array_map_schema.avro", "userid"),
    PAGEVIEWS("pageviews_schema.avro", "viewtime"),
    STOCK_TRADES("stock_trades_schema.avro", "symbol"),
    INVENTORY("inventory.avro", "id"),
    PRODUCT("product.avro", "id");

    private final String schemaFilename;
    private final String keyName;

    Quickstart(String schemaFilename, String keyName) {
      this.schemaFilename = schemaFilename;
      this.keyName = keyName;
    }

    public String getSchemaFilename() {
      return schemaFilename;
    }

    public String getSchemaKeyField() {
      return keyName;
    }
  }

  @Override
  public String version() {
    return VersionUtil.getVersion();
  }

  @Override
  public void start(Map<String, String> props) {
    config = new DatagenConnectorConfig(props);
    topic = config.getKafkaTopic();
    maxInterval = config.getMaxInterval();
    maxRecords = config.getIterations();
    schemaFilename = config.getSchemaFilename();
    schemaString = config.getSchemaString();
    schemaKeyField = config.getSchemaKeyfield();
    taskGeneration = 0;
    taskId = Integer.parseInt(props.get(TASK_ID));
    sourcePartition = Collections.singletonMap(TASK_ID, taskId);

    random = new Random();
    if (config.getRandomSeed() != null) {
      random.setSeed(config.getRandomSeed());
      // Each task will now deterministically advance it's random source
      // This makes it such that each task will generate different data
      for (int i = 0; i < taskId; i++) {
        random.setSeed(random.nextLong());
      }
    }

    Map<String, Object> offset = context.offsetStorageReader().offset(sourcePartition);
    if (offset != null) {
      //  The offset as it is stored contains our next state, so restore it as-is.
      taskGeneration = ((Long) offset.get(TASK_GENERATION)).intValue();
      count = ((Long) offset.get(CURRENT_ITERATION));
      random.setSeed((Long) offset.get(RANDOM_SEED));
    }

    Generator.Builder generatorBuilder = new Generator.Builder()
        .random(random)
        .generation(count);
    String quickstartName = config.getQuickstart();
    if (quickstartName != "") {
      try {
        quickstart = Quickstart.valueOf(quickstartName.toUpperCase());
        if (quickstart != null) {
          schemaFilename = quickstart.getSchemaFilename();
          schemaKeyField = schemaKeyField.equals("")
              ? quickstart.getSchemaKeyField() : schemaKeyField;
          try {
            generator = generatorBuilder
                .schemaStream(getClass().getClassLoader().getResourceAsStream(schemaFilename))
                .build();
          } catch (IOException e) {
            throw new ConnectException("Unable to read the '"
                + schemaFilename + "' schema file", e);
          }
        }
      } catch (IllegalArgumentException e) {
        log.warn("Quickstart '{}' not found: ", quickstartName, e);
      }
    } else if (schemaString != "") {
      generator = generatorBuilder.schemaString(schemaString).build();
    } else {
      String err = "Unable to read the '" + schemaFilename + "' schema file";
      try {
        generator = generatorBuilder.schemaStream(new FileInputStream(schemaFilename)).build();
      } catch (FileNotFoundException e) {
        // also look in jars on the classpath
        try {
          generator = generatorBuilder
              .schemaStream(DatagenTask.class.getClassLoader().getResourceAsStream(schemaFilename))
              .build();
        } catch (IOException inner) {
          throw new ConnectException(err, e);
        }
      } catch (IOException e) {
        throw new ConnectException(err, e);
      }
    }

    avroSchema = newSchema(generator.schema());

    if (!schemaKeyField.isEmpty() && avroSchema.getField(schemaKeyField) == null) {
      throw new ConfigException(DatagenConnectorConfig.SCHEMA_KEYFIELD_CONF, schemaKeyField,
              "Schema does not contain the specified field");
    }

    avroData = new AvroData(1);
    ksqlSchema = avroData.toConnectSchema(avroSchema);
  }

  public org.apache.avro.Schema newSchema(org.apache.avro.Schema avroSchema) {
    List<org.apache.avro.Schema.Field> fieldsToAdd = avroSchema.getFields().stream()
            .map(field -> new org.apache.avro.Schema.Field(field.name(), field.schema()))
            .collect(Collectors.toList());

    org.apache.avro.Schema.Type longSchema = org.apache.avro.Schema.Type.LONG;

    org.apache.avro.Schema tsSchema = org.apache.avro.Schema.create(longSchema);

    fieldsToAdd.add(new org.apache.avro.Schema.Field("currentTs", tsSchema));

    avroSchema = org.apache.avro.Schema.createRecord(
            avroSchema.getName(),
            avroSchema.getDoc(),
            avroSchema.getNamespace(),
            avroSchema.isError(),
            fieldsToAdd);

    return avroSchema;
  }

  @Override
  public List<SourceRecord> poll() throws InterruptedException {

    if (maxInterval > 0) {
      try {
        Thread.sleep((long) (maxInterval * Math.random()));
      } catch (InterruptedException e) {
        Thread.interrupted();
        return null;
      }
    }

    final Object generatedObject = generator.generate();
    if (!(generatedObject instanceof GenericRecord)) {
      throw new RuntimeException(String.format(
          "Expected Avro Random Generator to return instance of GenericRecord, found %s instead",
          generatedObject.getClass().getName()
      ));
    }
    GenericRecord randomAvroMessage = (GenericRecord) generatedObject;
    randomAvroMessage = newValue(randomAvroMessage);

    final List<Object> genericRowValues = new ArrayList<>();
    for (org.apache.avro.Schema.Field field : avroSchema.getFields()) {
      final Object value = randomAvroMessage.get(field.name());
      if (value instanceof Record) {
        final Record record = (Record) value;
        final Object ksqlValue = avroData.toConnectData(record.getSchema(), record).value();
        Object optionValue = getOptionalValue(ksqlSchema.field(field.name()).schema(), ksqlValue);
        genericRowValues.add(optionValue);
      } else {
        genericRowValues.add(value);
      }
    }

    // Key
    SchemaAndValue key = new SchemaAndValue(DEFAULT_KEY_SCHEMA, null);
    if (!schemaKeyField.isEmpty()) {
      key = avroData.toConnectData(
          randomAvroMessage.getSchema().getField(schemaKeyField).schema(),
          randomAvroMessage.get(schemaKeyField)
      );
    }

    // Value
    final org.apache.kafka.connect.data.Schema messageSchema = avroData.toConnectSchema(avroSchema);
    final Object messageValue = avroData.toConnectData(avroSchema, randomAvroMessage).value();

    if (maxRecords > 0 && count >= maxRecords) {
      throw new ConnectException(
          String.format("Stopping connector: generated the configured %d number of messages", count)
      );
    }

    // Re-seed the random each time so that we can save the seed to the source offsets.
    long seed = random.nextLong();
    random.setSeed(seed);

    // The source offsets will be the values that the next task lifetime will restore from
    // Essentially, the "next" state of the connector after this loop completes
    Map<String, Object> sourceOffset = new HashMap<>();
    // The next lifetime will be a member of the next generation.
    sourceOffset.put(TASK_GENERATION, taskGeneration + 1);
    // We will have produced this record
    sourceOffset.put(CURRENT_ITERATION, count + 1);
    // This is the seed that we just re-seeded for our own next iteration.
    sourceOffset.put(RANDOM_SEED, seed);

    final ConnectHeaders headers = new ConnectHeaders();
    headers.addLong(TASK_GENERATION, taskGeneration);
    headers.addLong(TASK_ID, taskId);
    headers.addLong(CURRENT_ITERATION, count);

    final List<SourceRecord> records = new ArrayList<>();
    SourceRecord record = new SourceRecord(
        sourcePartition,
        sourceOffset,
        topic,
        null,
        key.schema(),
        key.value(),
        messageSchema,
        messageValue,
        null,
        headers
    );
    records.add(record);
    count += records.size();
    return records;
  }

  public GenericRecord newValue(GenericRecord value) {
    GenericRecord newValue = new GenericData.Record(avroSchema);

    for (org.apache.avro.Schema.Field field: avroSchema.getFields()) {
      newValue.put(field.name(), value.get(field.name()));
    }

    newValue.put("currentTs", System.currentTimeMillis());

    return newValue;
  }

  @Override
  public void stop() {
  }

  private org.apache.kafka.connect.data.Schema getOptionalSchema(
      final org.apache.kafka.connect.data.Schema schema
  ) {
    switch (schema.type()) {
      case BOOLEAN:
        return org.apache.kafka.connect.data.Schema.OPTIONAL_BOOLEAN_SCHEMA;
      case INT32:
        return org.apache.kafka.connect.data.Schema.OPTIONAL_INT32_SCHEMA;
      case INT64:
        return org.apache.kafka.connect.data.Schema.OPTIONAL_INT64_SCHEMA;
      case FLOAT64:
        return org.apache.kafka.connect.data.Schema.OPTIONAL_FLOAT64_SCHEMA;
      case STRING:
        return org.apache.kafka.connect.data.Schema.OPTIONAL_STRING_SCHEMA;
      case ARRAY:
        return SchemaBuilder.array(getOptionalSchema(schema.valueSchema())).optional().build();
      case MAP:
        return SchemaBuilder.map(
            getOptionalSchema(schema.keySchema()),
            getOptionalSchema(schema.valueSchema())
        ).optional().build();
      case STRUCT:
        final SchemaBuilder schemaBuilder = SchemaBuilder.struct();
        for (Field field : schema.fields()) {
          schemaBuilder.field(
              field.name(),
              getOptionalSchema(field.schema())
          );
        }
        return schemaBuilder.optional().build();
      default:
        throw new ConnectException("Unsupported type: " + schema);
    }
  }

  private Object getOptionalValue(
      final org.apache.kafka.connect.data.Schema schema,
      final Object value
  ) {
    switch (schema.type()) {
      case BOOLEAN:
      case INT32:
      case INT64:
      case FLOAT64:
      case STRING:
        return value;
      case ARRAY:
        final List<?> list = (List<?>) value;
        return list.stream()
                   .map(listItem -> getOptionalValue(schema.valueSchema(), listItem))
                   .collect(Collectors.toList());
      case MAP:
        final Map<?, ?> map = (Map<?, ?>) value;
        return map.entrySet().stream()
            .collect(Collectors.toMap(
                k -> getOptionalValue(schema.keySchema(), k),
                v -> getOptionalValue(schema.valueSchema(), v)
            ));
      case STRUCT:
        final Struct struct = (Struct) value;
        final Struct optionalStruct = new Struct(getOptionalSchema(schema));
        for (Field field : schema.fields()) {
          optionalStruct.put(
              field.name(),
              getOptionalValue(
                  field.schema(),
                  struct.get(field.name())
              )
          );
        }
        return optionalStruct;
      default:
        throw new ConnectException("Invalid value schema: " + schema + ", value = " + value);
    }
  }
}
