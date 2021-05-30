package community.mlops

import community.mlops.models.{EcomWithCategory, EcomWithQuantityCanceled}
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import redis.clients.jedis.Jedis

import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import scala.util.Try

class RedisMapFunctionEcom(host: String, port: Int) extends RichMapFunction[EcomWithQuantityCanceled, EcomWithCategory] {
  private var jedis: Jedis = null

  override def map(value: EcomWithQuantityCanceled): EcomWithCategory = {
    val categ = jedis.get("elabDescCateg:" + value.Description.trim)

    if(Try(categ.toInt).isFailure) println(value.Description)
    if(Try(categ.toInt).isFailure) println(value)

//    val category = if(Try(categ.toInt).isSuccess) categ.toInt else 0
    val category = categ.toInt


    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val sdf2 = new SimpleDateFormat("MM/dd/yyyy HH:mm")


    val date = if (Try(sdf.parse(value.InvoiceDate)).isSuccess) {
      sdf.parse(value.InvoiceDate).toInstant.getEpochSecond
    } else if (Try(sdf2.parse(value.InvoiceDate)).isSuccess) {
      sdf2.parse(value.InvoiceDate).toInstant.getEpochSecond
    } else {
      sdf.parse(value.InvoiceDate).toInstant.getEpochSecond
    }

    EcomWithCategory(value.InvoiceNo, date, value.Quantity, value.UnitPrice, value.Description, value.CustomerID, category, value.QuantityCanceled)
  }

  override def open(parameters: Configuration): Unit = { // open connection to Redis
    jedis = new Jedis(host, port)
    //    jedis.auth("redispwd")
  }

  override def close(): Unit = { // close connection to Redis
    jedis.close()
  }
}
