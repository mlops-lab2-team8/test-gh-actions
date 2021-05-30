package community.mlops;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URISyntaxException;

@SpringBootApplication
//@EnableAsync
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
//    static final String SCHEMA_REGISTRY_URL = "http://izac-cp-schema-registry:8081";
//    static final String BOOTSTRAP_SERVER = "izac-cp-kafka-headless:9092";

    static final String SCHEMA_REGISTRY_URL = "http://34.69.13.112:30081";
    static final String BOOTSTRAP_SERVER = "kafka.cluster:31090";


    public static void main(String[] args) throws IOException, URISyntaxException {
        SpringApplication.run(Application.class, args);

        CachedSchemaRegistryClient cachedSchemaRegistryClient = new CachedSchemaRegistryClient(System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", Application.SCHEMA_REGISTRY_URL), 20);
        try {
            cachedSchemaRegistryClient.getAllSubjects();
        } catch (ConnectException | RestClientException e) {
            logger.error("schema registry is not available, exiting...");
            System.exit(400);
        }

        URIBuilder builder = new URIBuilder("http://localhost:8080" + "/"+System.getenv().getOrDefault("server.servlet.context-path", "")+"/admin/start");
        HttpPost post = new HttpPost(builder.build());
        HttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = client.execute(post);
        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
            out.append(line);
        }
        logger.info(out.toString());
    }
}