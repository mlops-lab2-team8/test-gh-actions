package community.mlops

import com.gojek.feast.{FeastClient, Row}
import com.google.gson.JsonParser
import com.google.protobuf.Int64Value
import io.grpc.{Channel, Grpc, ManagedChannel, ManagedChannelBuilder}
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClientBuilder
import org.json.simple.parser.JSONParser
import org.tensorflow.framework.{DataType, TensorProto}
import tensorflow.serving.{GetModelMetadata, Model, Predict, PredictionServiceGrpc}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest}
import java.io.{BufferedReader, InputStreamReader}
import java.util
import java.io._
import scala.io.Source

object Test {
  def main(args: Array[String]): Unit = {
//    val feastClient: FeastClient = FeastClient.create("34.134.172.205", 31542)
//
//    val features = feastClient.getOnlineFeatures(util.Arrays.asList("customer_info:Mean", "customer_info:categ_0", "customer_info:categ_1", "customer_info:categ_2", "customer_info:categ_3", "customer_info:categ_4"), util.Arrays.asList(Row.create().set("CustomerID", "17850")))
//
//    println(features.get(0).getFields)
//    println(features.get(0).getFields.get("customer_info:Mean").getDoubleVal)

    //

    val builder = new URIBuilder("http://35.226.123.203:80" + "/v1/models/cluster2:predict")



    val post = new HttpPost(builder.build)
    post.setHeader("Host", "cluster2.default.example.com")
    post.setEntity(new StringEntity("{\n" + "  \"instances\": [\n" + "    [36.7, 6.8,  2.8,  4.8,  1.4,  1.4]" + "  ]\n" + "}", ContentType.APPLICATION_JSON))
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
    println(jsonObject)


//    val rd = new BufferedReader(new InputStreamReader(response.getEntity.getContent))
//    val out = new StringBuilder
//    var line: String = ""
//    while (line != null ) {
//      line = rd.readLine
//      out.append(line)
//    }
//    System.out.println(out.toString)

//    val httpClient = HttpClientBuilder.create.build
//    val httpResponse = httpClient.execute(new HttpPost(builder.build))
//    val entity = httpResponse.getEntity
//    var content = ""
//    if (entity != null) {
//      val inputStream = entity.getContent
//      content = Source.fromInputStream(inputStream).getLines.mkString
//      inputStream.close()
//    }
//    httpClient.getConnectionManager.shutdown()
//    content




//    val client = java.net.http.HttpClient.newHttpClient
//
//    val request = HttpRequest.newBuilder(URI.create("http://35.226.123.203:80" + "/v1/models/cluster:predict")).header("Host", "cluster.default.example.com").build
//
//    val response = client.send(request, new Nothing(classOf[Nothing]))


//    val channel = ManagedChannelBuilder.forAddress("host", 1).build
//
//    val stub = new PredictionServiceGrpc.PredictionServiceStub(channel)
//
//    val request: Predict.PredictRequest = Predict.PredictRequest.newBuilder().setModelSpec(Model.ModelSpec.newBuilder().setName("tensorflow"))
//      .build()
//
//
//    stub.predict(request)


/*
    val host = "35.226.123.203"
    val port = 80
    // the model's name.
    val modelName = "cluster-grpc"
    // model's version
    val modelVersion = 1

    val hostName = "cluster-grpc.default.example.com"
    // create a channel
    val channel: ManagedChannel = GrpcClient.builder(host, port, hostName)
    val stub = tensorflow.serving.PredictionServiceGrpc.newBlockingStub(channel)

    // create PredictRequest
    val requestBuilder = Predict.PredictRequest.newBuilder

    // create ModelSpec
    val modelSpecBuilder = tensorflow.serving.Model.ModelSpec.newBuilder
    modelSpecBuilder.setName(modelName)
    modelSpecBuilder.setVersion(Int64Value.of(modelVersion))
    modelSpecBuilder.setSignatureName("serving_default")

    // set model for request
    requestBuilder.setModelSpec(modelSpecBuilder)

    // create TensorProto with 3 floats
    val tensorProtoBuilder = org.tensorflow.framework.TensorProto.newBuilder
    tensorProtoBuilder.setDtype(DataType.DT_FLOAT)
    tensorProtoBuilder.addFloatVal(1.0f)
    tensorProtoBuilder.addDoubleVal(1.0f)
    tensorProtoBuilder.addDoubleVal(1.0f)



    // create TensorShapeProto
    val tensorShapeBuilder = org.tensorflow.framework.TensorShapeProto.newBuilder
    tensorShapeBuilder.addDim(org.tensorflow.framework.TensorShapeProto.Dim.newBuilder.setSize(3))

    // set shape for proto
    tensorProtoBuilder.setTensorShape(tensorShapeBuilder.build)

    // build proto
    val proto = tensorProtoBuilder.build



//    MINE
    val tensorProtoBuilder2 = org.tensorflow.framework.TensorProto.newBuilder
    tensorProtoBuilder2.addDoubleVal(1D)
    tensorProtoBuilder2.addDoubleVal(1D)
    tensorProtoBuilder2.addDoubleVal(1D)
    tensorProtoBuilder2.addDoubleVal(1D)
    tensorProtoBuilder2.addDoubleVal(1D)
    tensorProtoBuilder2.addDoubleVal(1D)


    val proto2 = tensorProtoBuilder2.build

    //    MINE


    // set proto for request
    requestBuilder.putInputs("dense_input", proto2)

    // build request
    val request = requestBuilder.build
    System.out.println("Printing request \n" + request.toString)

    // run predict
    val response = stub.predict(request)
    System.out.println(response.toString)*/

  }

}
