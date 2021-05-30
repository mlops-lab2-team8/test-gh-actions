package community.mlops

import org.apache.flink.api.scala.createTypeInformation
import org.apache.flink.formats.avro.registry.confluent.{ConfluentRegistryAvroDeserializationSchema, ConfluentRegistryAvroSerializationSchema}
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaProducer}

import java.util.Properties
import scala.util.Try

object StreamingJob {

  @throws[Exception]
  def main(args: Array[String]): Unit = {
    val schemaRegistryUrl = if (Try(args(0)).isFailure) "http://34.69.13.112:30081" else args(0)
    val kafkaBroker = if (Try(args(1)).isFailure) "kafka.cluster:31090" else args(1)

    val inputTopic = if (Try(args(2)).isFailure) "datagen_ecom_datagen_test" else args(2)
    val outputTopic = if (Try(args(3)).isFailure) "datagen_ecom_datagen_test_output3" else args(3)
    val kafkaConsumerGroup = if (Try(args(4)).isFailure) "flink" + System.currentTimeMillis() else args(4)


    val feastHost = if (Try(args(5)).isFailure) "34.134.172.205" else args(5)
    val feastPort = if (Try(args(6)).isFailure) 31542 else args(6).toInt

    val istioExternal = if (Try(args(7)).isFailure) "35.226.123.203" else args(7)

    val segmentationModel = if (Try(args(8)).isFailure) "35.226.123.203" else args(8)

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(3)



    val flinkKafkaConsumer = createAvroDatagenConsumerForTopic(inputTopic, kafkaBroker, kafkaConsumerGroup, schemaRegistryUrl)

    val flinkAvroKafkaProducer = createAvroProducerForTopic(outputTopic, kafkaBroker, schemaRegistryUrl)


    flinkKafkaConsumer.setStartFromEarliest()

    val stream = env.addSource(flinkKafkaConsumer)


    stream
      .map(new FeastMapFunction(feastHost, feastPort, istioExternal, segmentationModel))
      //      .print()
      .addSink(flinkAvroKafkaProducer)

    env.execute("Flink Streaming Sala API Skeleton")
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

  def createAvroProducerForTopic(topic: String, kafkaAddress: String, schemaRegistryUrl: String): FlinkKafkaProducer[CustomerPrediction] = {

    val props = new Properties
    props.setProperty("bootstrap.servers", kafkaAddress)

    new FlinkKafkaProducer[CustomerPrediction](topic, ConfluentRegistryAvroSerializationSchema.forSpecific(classOf[CustomerPrediction], topic + "-value", schemaRegistryUrl), props)
  }

//  def createPlainAvroProducerForTopic(topic: String, kafkaAddress: String): FlinkKafkaProducer[EcomFeatures] = {
//
//    val props = new Properties
//    props.setProperty("bootstrap.servers", kafkaAddress)
//
//    new FlinkKafkaProducer[EcomFeatures](topic, AvroSerializationSchema.forSpecific(classOf[EcomFeatures]), props)
//  }

  /*  def createProtoProducerForTopic(topic: String, kafkaAddress: String, kafkaGroup: String, schemaRegistryUrl: String): FlinkKafkaProducer[EcomFeaturesProtobuf] = {

      val props = new Properties
      props.setProperty("bootstrap.servers", kafkaAddress)
      props.setProperty("group.id", kafkaGroup)

      new FlinkKafkaProducer[EcomFeaturesProtobuf](topic, new KafkaGenericProtoSerializationSchema(schemaRegistryUrl, topic), props, FlinkKafkaProducer.Semantic.EXACTLY_ONCE)
    }*/

}
