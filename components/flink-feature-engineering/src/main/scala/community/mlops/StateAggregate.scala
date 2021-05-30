package community.mlops

import com.google.protobuf.Timestamp
import community.mlops.models.{EcomWithCategory, State}
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration

import java.text.SimpleDateFormat
import java.time.Instant
import scala.collection.mutable

class StateAggregate extends RichMapFunction[EcomWithCategory, EcomFeatures]{
  private var state: ValueState[State] = _

  override def open(parameters: Configuration): Unit = {
    state = getRuntimeContext.getState(
      new ValueStateDescriptor[State]("state", classOf[State])
    )
  }

  override def map(value: EcomWithCategory): EcomFeatures = {
    val tmpState = state.value

    val currentState = if (tmpState != null) {
      tmpState
    } else {
      models.State(0, value.Quantity * value.UnitPrice, 0, 0, 0, 0, mutable.Map().withDefaultValue(0), mutable.Map().withDefaultValue(0))
    }

    val amount = value.UnitPrice * value.Quantity

    val newCount = currentState.Count + 1
    val newMin = Math.min(currentState.Min, amount)
    val newMax = Math.max(currentState.Max, amount)
    val newSum = currentState.Sum + amount
    val newMean = newSum / newCount
    val categMap = currentState.CategMap
    categMap(value.Category + ":count") += 1
    categMap(value.Category + ":sum") += amount
    categMap(value.Category + ":avg") = categMap(value.Category + ":sum") / categMap(value.Category + ":count")

    val QuantityCanceledMap = currentState.QuantityCanceledMap
    QuantityCanceledMap(value.Description) = value.QuantityCanceled

    val totalQuantityCanceled = QuantityCanceledMap.values.sum

    val newState = State(newCount, newMin, newMax, newMean, newSum, value.Category, categMap, QuantityCanceledMap)

    state.update(newState)

    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    val ts = Timestamp.newBuilder()
      .setSeconds(value.InvoiceDate)
      .build()

    /*EcomFeaturesProtobuf.newBuilder()
      .setCustomerId(value.CustomerID)
      .setCount(newCount)
//      .setInvoiceDate(sdf.format(value.InvoiceDate * 1000))
      .setInvoiceDate(ts)
      .setMin(newMin)
      .setMax(newMax)
      .setMean(newMean)
      .setSum(newSum)
      .setCateg0(categMap("0:avg"))
      .setCateg1(categMap("1:avg"))
      .setCateg2(categMap("2:avg"))
      .setCateg3(categMap("3:avg"))
      .setCateg4(categMap("4:avg"))
      .build()*/

    val r = scala.util.Random


    new EcomFeatures(value.CustomerID, newCount, Instant.ofEpochSecond(value.InvoiceDate), newMin, newMax, newMean, newSum,
      categMap("0:avg"), categMap("1:avg"), categMap("2:avg"), categMap("3:avg"), categMap("4:avg"), totalQuantityCanceled, r.nextInt(2))
  }

}

