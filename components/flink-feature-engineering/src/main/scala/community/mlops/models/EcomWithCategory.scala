package community.mlops.models

case class EcomWithCategory(InvoiceNo: String, InvoiceDate: Long, Quantity: Long, UnitPrice: Double, Description: String, CustomerID: String, Category: Int, QuantityCanceled: Int) {

}
