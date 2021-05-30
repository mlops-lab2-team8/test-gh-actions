package community.mlops

import org.apache.flink.api.scala.{DataSet, ExecutionEnvironment, createTypeInformation}
import org.apache.flink.configuration.Configuration

import java.util
import scala.collection.mutable.Map
import java.util.{Map, Properties}

object Batch {
  case class Page(InvoiceNo: String, StockCode: String, Description: String, Quantity: Long, InvoiceDate: String)

  def main(args: Array[String]): Unit = {
    val map: util.Map[String, String] = new util.HashMap[String, String]()
    map.put("s3.access-key", "minio")
    map.put("s3.secret-key", "minio123")

    import org.apache.flink.configuration.Configuration
    import org.apache.flink.configuration.ConfigurationUtils
    val props = new Properties()
    props.put("s3.access-key", "minio")
    props.put("s3.secret-key", "minio123")
//    props.put("metrics.reporter.jmx.factory.class", "org.apache.flink.metrics.jmx.JMXReporterFactory")
    val conf = ConfigurationUtils.createConfiguration(props)

//    val customConfiguration = Configuration.fromMap(map)
//    customConfiguration.setString("s3.access-key", "minio")
//    customConfiguration.setString("s3.secret-key", "minio123")
//    customConfiguration.setString("s3.path.style.access", "true")

    val env = ExecutionEnvironment.createLocalEnvironment(conf)

    val csvInput = env.readCsvFile[Page]("s3://raw/34.69.13.112:31137")
    csvInput.print()

    import org.apache.flink.configuration.GlobalConfiguration
    import org.apache.flink.core.fs.FileSystem

    FileSystem.initialize(GlobalConfiguration.loadConfiguration(System.getenv("FLINK_CONF_DIR")))

    env.execute("asds")
  }

}
