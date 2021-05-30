package community.mlops

import community.mlops.models.{EcomWithQuantityCanceled, State}
import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration

class QuantityAggregate extends RichMapFunction[Ecom, EcomWithQuantityCanceled]{
  private var state: ValueState[EcomWithQuantityCanceled] = _

  override def open(parameters: Configuration): Unit = {
    state = getRuntimeContext.getState(
      new ValueStateDescriptor[EcomWithQuantityCanceled]("quantityState", classOf[EcomWithQuantityCanceled])
    )
  }

  override def map(value: Ecom): EcomWithQuantityCanceled = {
    val tmpState = state.value

    val currentState = if (tmpState != null) {
      tmpState
    } else {
      EcomWithQuantityCanceled(value.getInvoiceNo.toString, value.getDescription.toString, 0, value.getInvoiceDate.toString, value.getUnitPrice.toString.toDouble, value.getCustomerID.toString, 0)
    }

    val newQuantity = value.getQuantity.toString.toInt

    val quantity = if (newQuantity > 0) currentState.Quantity + newQuantity else currentState.Quantity
    val quantityCanceled = if (newQuantity < 0) currentState.QuantityCanceled - newQuantity else currentState.QuantityCanceled

    val newState = EcomWithQuantityCanceled(currentState.InvoiceNo, currentState.Description, quantity, currentState.InvoiceDate, currentState.UnitPrice, currentState.CustomerID, quantityCanceled)

    state.update(newState)

    newState
  }

}
