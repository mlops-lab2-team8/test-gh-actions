package community.mlops
import com.gojek.feast.{FeastClient, Row}
import com.google.gson.JsonParser
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClientBuilder

import java.time.Instant
import java.util
import scala.io.Source

class FeastMapFunction(feastHost: String, feastPort: Int, istioExternal: String, segmentationModel: String) extends RichMapFunction[EcomDatagen, CustomerPrediction] {


  private var feastClient: FeastClient = null

  override def map(value: EcomDatagen): CustomerPrediction = {
    val features = feastClient.getOnlineFeatures(util.Arrays.asList("customer_info:Count", "customer_info:Sum", "customer_info:Min", "customer_info:Max", "customer_info:Mean", "customer_info:categ_0", "customer_info:categ_1", "customer_info:categ_2", "customer_info:categ_3", "customer_info:categ_4", "customer_info:QuantityCanceled", "customer_info:Churn"), util.Arrays.asList(Row.create().set("CustomerID", value.getCustomerID.toString)))

    val mean = features.get(0).getFields.get("customer_info:Mean").getDoubleVal
    val max = features.get(0).getFields.get("customer_info:Max").getDoubleVal
    val min = features.get(0).getFields.get("customer_info:Min").getDoubleVal
    val sum = features.get(0).getFields.get("customer_info:Sum").getDoubleVal
    val count = features.get(0).getFields.get("customer_info:Count").getInt64Val
    val categ_0 = features.get(0).getFields.get("customer_info:categ_0").getDoubleVal
    val categ_1 = features.get(0).getFields.get("customer_info:categ_1").getDoubleVal
    val categ_2 = features.get(0).getFields.get("customer_info:categ_2").getDoubleVal
    val categ_3 = features.get(0).getFields.get("customer_info:categ_3").getDoubleVal
    val categ_4 = features.get(0).getFields.get("customer_info:categ_4").getDoubleVal
    val quantityCanceled = features.get(0).getFields.get("customer_info:QuantityCanceled").getInt32Val
    val churn = features.get(0).getFields.get("customer_info:Churn").getInt32Val



    val prediction = clusterPrediction(mean, categ_0, categ_1, categ_2, categ_3, categ_4)

    CustomerPrediction.newBuilder()
      .setMax(max)
      .setMin(min)
      .setSum(sum)
      .setMean(mean)
      .setCount(count)
      .setCateg0(categ_0)
      .setCateg1(categ_1)
      .setCateg2(categ_2)
      .setCateg3(categ_3)
      .setCateg4(categ_4)
      .setQuantity(value.getQuantity)
      .setQuantityCanceled(quantityCanceled)
      .setChurn(churn)
      .setCountry(value.getCountry)
      .setCurrentTs(value.getCurrentTs)
      .setCustomerID(value.getCustomerID)
      .setDescription(value.getDescription)
      .setUnitPrice(value.getUnitPrice)
      .setInvoiceDate(Instant.ofEpochSecond(value.getInvoiceDate))
      .setInvoiceNo(value.getInvoiceNo)
      .setPrediction(prediction)
      .setPredictionTs(System.currentTimeMillis())
      .build()
  }

  def clusterPrediction(mean: Double, categ_0: Double, categ_1: Double, categ_2: Double, categ_3: Double, categ_4: Double): Double = {
    val builder = new URIBuilder(s"http://${istioExternal}:80" + s"/v1/models/${segmentationModel}:predict")

    val post = new HttpPost(builder.build)
    post.setHeader("Host", "cluster2.default.example.com")
    post.setEntity(new StringEntity("{" + "\"instances\": [" + s"[${mean}, ${categ_0}, ${categ_1}, ${categ_2}, ${categ_3}, ${categ_4}]" + "]}", ContentType.APPLICATION_JSON))
    val client = HttpClientBuilder.create.build

    val response = client.execute(post)
    val entity = response.getEntity
    var content = ""
    if (entity != null) {
      val inputStream = entity.getContent
      content = Source.fromInputStream(inputStream).getLines.mkString
      inputStream.close()
    }
    client.getConnectionManager.shutdown()
    val jsonObject = JsonParser.parseString(content).getAsJsonObject

    val jsonArray = jsonObject.get("predictions").getAsJsonArray.get(0).getAsJsonArray
    var prediction = 0
    for(i <- 0 to 10) {
      if (jsonArray.get(i).getAsDouble == 1.0) {
        prediction = i
      }
    }

    prediction
  }

  override def open(parameters: Configuration): Unit = { // open connection to Redis
    feastClient = FeastClient.create(feastHost, feastPort)
  }

  override def close(): Unit = { // close connection to Redis
    feastClient.close()
  }
}
