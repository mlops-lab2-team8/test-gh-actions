package community.mlops

import community.mlops.models.{EcomWithQuantityCanceledDatagen}
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration

class QuantityAggregateEcomDatagen extends RichMapFunction[EcomDatagen, EcomWithQuantityCanceledDatagen]{
  private var state: ValueState[EcomWithQuantityCanceledDatagen] = _

  override def open(parameters: Configuration): Unit = {
    state = getRuntimeContext.getState(
      new ValueStateDescriptor[EcomWithQuantityCanceledDatagen]("quantityState", classOf[EcomWithQuantityCanceledDatagen])
    )
  }

  override def map(value: EcomDatagen): EcomWithQuantityCanceledDatagen = {
    val tmpState = state.value

    val currentState = if (tmpState != null) {
      tmpState
    } else {
      EcomWithQuantityCanceledDatagen(value.getInvoiceNo, value.getDescription.toString, value.getQuantity.toString.toInt, value.getInvoiceDate, value.getUnitPrice.toString.toDouble, value.getCustomerID.toString, 0)
    }

    val newQuantity = value.getQuantity.toString.toInt

    val quantity = if (newQuantity >= 0) currentState.Quantity.toInt + newQuantity else currentState.Quantity
    val quantityCanceled = if (newQuantity >= 0) currentState.QuantityCanceled else currentState.QuantityCanceled - newQuantity


    EcomWithQuantityCanceledDatagen(currentState.InvoiceNo, currentState.Description, quantity, currentState.InvoiceDate, currentState.UnitPrice, currentState.CustomerID, quantityCanceled)
  }
}
