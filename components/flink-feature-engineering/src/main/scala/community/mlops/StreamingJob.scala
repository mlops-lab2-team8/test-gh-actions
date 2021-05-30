package community.mlops

import com.google.protobuf.DynamicMessage
import community.mlops.models.EcomWithQuantityCanceled
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.api.scala.createTypeInformation
import org.apache.flink.formats.avro.AvroSerializationSchema
import org.apache.flink.formats.avro.registry.confluent.{ConfluentRegistryAvroDeserializationSchema, ConfluentRegistryAvroSerializationSchema}
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaProducer}

import java.util.Properties
import scala.util.Try

object StreamingJob {

  @throws[Exception]
  def main(args: Array[String]): Unit = {
    val schemaRegistryUrl = if (Try(args(0)).isFailure) "http://izac-cp-schema-registry:8081" else args(0)
    val kafkaBroker = if (Try(args(1)).isFailure) "izac-cp-kafka-headless:9092" else args(1)

    val inputTopicTraining = if (Try(args(2)).isFailure) "elab_raw6" else args(2)
    val outputTopicTraining = if (Try(args(3)).isFailure) "datagen_ecom_test_output" else args(3)
    val kafkaConsumerGroupTraining = if (Try(args(4)).isFailure) "flink" + System.currentTimeMillis() else args(4)

    val inputTopicPrediction = if (Try(args(5)).isFailure) "datagen_ecom_datagen_test" else args(5)
    val outputTopicPrediction = if (Try(args(6)).isFailure) "datagen_ecom_datagen_test_output" else args(6)
    val kafkaConsumerGroupPrediction = if (Try(args(7)).isFailure) "flink" + System.currentTimeMillis() else args(7)

    val runTrainPipeline = if (Try(args(8)).isFailure) "true" else args(8)
    val runPredictionPipeline = if (Try(args(9)).isFailure) "true" else args(9)

    val redisHost = if (Try(args(10)).isFailure) "izac-redis-master" else args(10)
    val redisPort = if (Try(args(11)).isFailure) "6379" else args(11)

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(3)


    if (runTrainPipeline.equals("true")) ecomTrainingPipeline(env, kafkaBroker, schemaRegistryUrl, inputTopicTraining, outputTopicTraining, kafkaConsumerGroupTraining, redisHost, redisPort.toInt)

    if (runPredictionPipeline.equals("true")) ecomServingPipeline(env, kafkaBroker, schemaRegistryUrl, inputTopicPrediction, outputTopicPrediction, kafkaConsumerGroupPrediction, redisHost, redisPort.toInt)

    env.execute("Flink Streaming Sala API Skeleton")
  }


  def ecomTrainingPipeline(env: StreamExecutionEnvironment, kafkaBroker: String, schemaRegistryUrl: String, inputTopic: String, outputTopic: String, kafkaConsumerGroup: String, redisHost: String, redisPort: Int): Unit = {

    val flinkKafkaConsumer = createAvroConsumerForTopic(inputTopic, kafkaBroker, kafkaConsumerGroup, schemaRegistryUrl)


    val flinkKafkaProducer = createAvroProducerForTopic(outputTopic, kafkaBroker, schemaRegistryUrl)

    flinkKafkaConsumer.setStartFromEarliest()

    val stream = env.addSource(flinkKafkaConsumer)


    stream
      .filter(_.getDescription != null)
      .filter(_.getDescription.toString != "")
      .filter(_.getDescription.toString != "null")
      .filter(_.getCustomerID != null)
      .filter(_.getCustomerID.toString != "")
      .filter(_.getCustomerID.toString != "null")
      .map(x => {
        x.setInvoiceNo(if (x.getInvoiceNo.toString.charAt(0) == 'C')  x.getInvoiceNo.toString.substring(1) else x.getInvoiceNo.toString)
        x
      })
      .keyBy(new KeySelector[Ecom, Tuple2[String, String]]() {
        override def getKey(value: Ecom): Tuple2[String, String] = (value.getCustomerID.toString, value.getStockCode.toString)
      })
      .map(new QuantityAggregate)
      .filter(x => (x.Quantity - x.QuantityCanceled) > 0)
      .map(new RedisMapFunctionEcom(redisHost, redisPort))
      .keyBy(_.CustomerID)
      .map(new StateAggregate)
      .addSink(flinkKafkaProducer)

  }

  def ecomServingPipeline(env: StreamExecutionEnvironment, kafkaBroker: String, schemaRegistryUrl: String, inputTopic: String, outputTopic: String, kafkaConsumerGroup: String, redisHost: String, redisPort: Int): Unit = {

    val flinkKafkaConsumer = createAvroDatagenConsumerForTopic(inputTopic, kafkaBroker, kafkaConsumerGroup, schemaRegistryUrl)

    val flinkAvroKafkaProducer = createPlainAvroProducerForTopic(outputTopic, kafkaBroker)


    flinkKafkaConsumer.setStartFromEarliest()

    val stream = env.addSource(flinkKafkaConsumer)


    stream
      .filter(_.getDescription != null)
      .filter(_.getDescription.toString != "")
      .filter(_.getDescription.toString != "null")
      .filter(_.getCustomerID != null)
      .filter(_.getCustomerID.toString != "")
      .filter(_.getCustomerID.toString != "null")
      .keyBy(new KeySelector[EcomDatagen, Tuple3[String, String, String]]() {
        override def getKey(value: EcomDatagen): Tuple3[String, String, String] = (value.getCustomerID.toString, value.getInvoiceNo.toString, value.getDescription.toString)
      })
      .map(new QuantityAggregateEcomDatagen)
      .filter(x => (x.Quantity - x.QuantityCanceled) > 0)
      .map(new RedisMapFunctionEcomDatagen(redisHost, redisPort))
      .keyBy(_.CustomerID)
      .map(new StateAggregate)
      .addSink(flinkAvroKafkaProducer)
  }


  def createAvroConsumerForTopic(topic: String, kafkaAddress: String, kafkaGroup: String, schemaRegistryUrl: String): FlinkKafkaConsumer[Ecom] = {

    val props = new Properties
    props.setProperty("bootstrap.servers", kafkaAddress)
    props.setProperty("group.id", kafkaGroup)

    new FlinkKafkaConsumer[Ecom](topic, ConfluentRegistryAvroDeserializationSchema.forSpecific(classOf[Ecom], schemaRegistryUrl, 1000), props)
  }

  def createAvroDatagenConsumerForTopic(topic: String, kafkaAddress: String, kafkaGroup: String, schemaRegistryUrl: String): FlinkKafkaConsumer[EcomDatagen] = {

    val props = new Properties
    props.setProperty("bootstrap.servers", kafkaAddress)
    props.setProperty("group.id", kafkaGroup)

    new FlinkKafkaConsumer[EcomDatagen](topic, ConfluentRegistryAvroDeserializationSchema.forSpecific(classOf[EcomDatagen], schemaRegistryUrl, 1000), props)
  }

  def createAvroProducerForTopic(topic: String, kafkaAddress: String, schemaRegistryUrl: String): FlinkKafkaProducer[EcomFeatures] = {

    val props = new Properties
    props.setProperty("bootstrap.servers", kafkaAddress)

    new FlinkKafkaProducer[EcomFeatures](topic, ConfluentRegistryAvroSerializationSchema.forSpecific(classOf[EcomFeatures], topic + "-value", schemaRegistryUrl), props)
  }

  def createPlainAvroProducerForTopic(topic: String, kafkaAddress: String): FlinkKafkaProducer[EcomFeatures] = {

    val props = new Properties
    props.setProperty("bootstrap.servers", kafkaAddress)

    new FlinkKafkaProducer[EcomFeatures](topic, AvroSerializationSchema.forSpecific(classOf[EcomFeatures]), props)
  }

/*  def createProtoProducerForTopic(topic: String, kafkaAddress: String, kafkaGroup: String, schemaRegistryUrl: String): FlinkKafkaProducer[EcomFeaturesProtobuf] = {

    val props = new Properties
    props.setProperty("bootstrap.servers", kafkaAddress)
    props.setProperty("group.id", kafkaGroup)

    new FlinkKafkaProducer[EcomFeaturesProtobuf](topic, new KafkaGenericProtoSerializationSchema(schemaRegistryUrl, topic), props, FlinkKafkaProducer.Semantic.EXACTLY_ONCE)
  }*/

}
