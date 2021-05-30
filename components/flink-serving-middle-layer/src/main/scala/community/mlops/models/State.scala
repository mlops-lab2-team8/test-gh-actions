package community.mlops.models

import scala.collection.mutable

case class State(Count: Long, Min: Double, Max: Double, Mean: Double, Sum: Double, Categ: Int, CategMap: mutable.Map[String, Double]) {
}
