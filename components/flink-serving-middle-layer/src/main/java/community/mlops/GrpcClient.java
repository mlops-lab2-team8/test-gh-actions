package community.mlops;

import com.google.protobuf.ByteString;
import com.google.protobuf.Int64Value;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import tensorflow.serving.Classification;
import tensorflow.serving.Classification.ClassificationRequest;
import tensorflow.serving.PredictionServiceGrpc;
import tensorflow.serving.PredictionServiceGrpc.PredictionServiceBlockingStub;
import tensorflow.serving.Model.ModelSpec;
import org.tensorflow.example.Example;
import tensorflow.serving.InputOuterClass.Input;
import tensorflow.serving.InputOuterClass.ExampleList;
import org.tensorflow.example.Features;

import java.util.HashMap;
import java.util.Map;

public class GrpcClient {

    public static void main(String[] args) throws Exception {

        String host = "localhost";
        int port = 8500;
        String modelName = "Model_1";
        long modelVersion = 1;

        // create a channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        PredictionServiceBlockingStub stub = PredictionServiceGrpc.newBlockingStub(channel);

        // create ClassificationRequest
        ClassificationRequest.Builder classificationRequestBuilder = ClassificationRequest.newBuilder();

        // create ModelSpec
        ModelSpec.Builder modelSpecBuilder = ModelSpec.newBuilder();
        modelSpecBuilder.setName(modelName);
        modelSpecBuilder.setVersion(Int64Value.of(modelVersion));
        modelSpecBuilder.setSignatureName("serving_default");

        // set model for request
        classificationRequestBuilder.setModelSpec(modelSpecBuilder);

        // map of input features
        Map<String, Float> myMap = new HashMap<>();
        myMap.put("feature1", 1.0f);
        myMap.put("feature2", 0.0f);

        // convert map to example list
        ExampleList.Builder exampleListBuilder = ExampleList.newBuilder();
        exampleListBuilder.addExamples(buildExample(myMap));
        ExampleList exampleList = exampleListBuilder.build();

        // create input
        Input.Builder inputBuilder = Input.newBuilder();
        inputBuilder.setExampleList(exampleList);

        // set input for request
        Input input = inputBuilder.build();
        classificationRequestBuilder.setInput(input);

        // build request
        Classification.ClassificationRequest request = classificationRequestBuilder.build();

        // run classification
        Classification.ClassificationResponse response = stub.classify(request);

        System.out.println(response.toString());
    }

    public static ManagedChannel builder(String host, Integer port, String hostName) {
        return ManagedChannelBuilder.forAddress(host, port).overrideAuthority(hostName).usePlaintext().build();
    }

    public static Example buildExample(Map<String, ?> featureMap) {
        Features.Builder featuresBuilder = Features.newBuilder();
        for (String attr : featureMap.keySet()) {
            Object value = featureMap.get(attr);
            if (value instanceof Float) {
                featuresBuilder.putFeature(attr, feature((Float) value));
            } else if (value instanceof float[]) {
                featuresBuilder.putFeature(attr, feature((float[]) value));
            } else if (value instanceof String) {
                featuresBuilder.putFeature(attr, feature((String) value));
            } else if (value instanceof String[]) {
                featuresBuilder.putFeature(attr, feature((String[]) value));
            } else if (value instanceof Long) {
                featuresBuilder.putFeature(attr, feature((Long) value));
            } else if (value instanceof long[]) {
                featuresBuilder.putFeature(attr, feature((long[]) value));
            } else {
                throw new UnsupportedOperationException("Not supported attribute value data type!");
            }
        }
        Features features = featuresBuilder.build();
        return Example.newBuilder().setFeatures(features).build();
    }

    private static org.tensorflow.example.Feature feature(String... strings) {
        org.tensorflow.example.BytesList.Builder b = org.tensorflow.example.BytesList.newBuilder();
        for (String s : strings) {
            b.addValue(ByteString.copyFromUtf8(s));
        }
        return org.tensorflow.example.Feature.newBuilder().setBytesList(b).build();
    }

    private static org.tensorflow.example.Feature feature(float... values) {
        org.tensorflow.example.FloatList.Builder b = org.tensorflow.example.FloatList.newBuilder();
        for (float v : values) {
            b.addValue(v);
        }
        return org.tensorflow.example.Feature.newBuilder().setFloatList(b).build();
    }

    private static org.tensorflow.example.Feature feature(long... values) {
        org.tensorflow.example.Int64List.Builder b = org.tensorflow.example.Int64List.newBuilder();
        for (long v : values) {
            b.addValue(v);
        }
        return org.tensorflow.example.Feature.newBuilder().setInt64List(b).build();
    }
}
