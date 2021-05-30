package community.mlops
import community.mlops.models.{EcomWithCategory, EcomWithQuantityCanceledDatagen}
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import redis.clients.jedis.Jedis

import java.text.SimpleDateFormat
import scala.util.Try

class RedisMapFunctionEcomDatagen(host: String, port: Int) extends RichMapFunction[EcomWithQuantityCanceledDatagen, EcomWithCategory] {


  private var jedis: Jedis = null

  override def map(value: EcomWithQuantityCanceledDatagen): EcomWithCategory = {
    val categ = jedis.get("elabDescCateg:" + value.Description)

    val category = if(Try(categ.toInt).isSuccess) categ.toInt else 0



    EcomWithCategory(value.InvoiceNo.toString, value.InvoiceDate, value.Quantity, value.UnitPrice, value.Description, value.CustomerID, category, value.QuantityCanceled)
  }

  override def open(parameters: Configuration): Unit = { // open connection to Redis
    jedis = new Jedis(host, port)
//    jedis.auth("redispwd")
  }

  override def close(): Unit = { // close connection to Redis
    jedis.close()
  }
}
