//package community.mlops
//
//
//import com.google.protobuf.{DynamicMessage, Message}
//import community.mlops.EcomFeaturesProtobufOuterClass.EcomFeaturesProtobuf
//import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
//import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
//import io.confluent.kafka.serializers.protobuf.{KafkaProtobufDeserializer, KafkaProtobufDeserializerConfig, KafkaProtobufSerializer}
//import org.apache.flink.api.common.typeinfo.TypeInformation
//import org.apache.flink.api.java.typeutils.TypeExtractor
//import org.apache.flink.streaming.connectors.kafka.{KafkaDeserializationSchema, KafkaSerializationSchema}
//import org.apache.kafka.clients.consumer.ConsumerRecord
//import org.apache.kafka.clients.producer.ProducerRecord
//
//import java.{lang, util}
//
//class KafkaGenericProtoSerializationSchema(val registryUrl: String, val topic: String) extends KafkaSerializationSchema[EcomFeaturesProtobuf] {
//  private var inner: KafkaProtobufSerializer[EcomFeaturesProtobuf] = null
//
//
//    def getProducedType: TypeInformation[EcomFeaturesProtobuf] = TypeExtractor.getForClass(classOf[EcomFeaturesProtobuf])
//
//
//
//  private def checkInitialized(): Unit = {
//    if (inner == null) {
//      val props = new util.HashMap[String, String]
//      props.put(KafkaProtobufDeserializerConfig.DERIVE_TYPE_CONFIG, "false")
//      props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, registryUrl);
//      val client = new CachedSchemaRegistryClient(registryUrl,  AbstractKafkaSchemaSerDeConfig.MAX_SCHEMAS_PER_SUBJECT_DEFAULT)
//      inner = new KafkaProtobufSerializer[EcomFeaturesProtobuf](client, props)
//    }
//  }
//
//
//  override def serialize(record: EcomFeaturesProtobuf, aLong: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
//    checkInitialized()
//    new ProducerRecord[Array[Byte], Array[Byte]](topic, inner.serialize(topic, record))
//  }
//}
