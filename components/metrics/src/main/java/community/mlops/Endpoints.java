package community.mlops;

import community.mlops.models.Query;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.state.HostInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import javax.validation.Valid;
import java.io.IOException;

@RestController
@Api(tags = { "Endpoints" })
public class Endpoints {
    private static final Logger logger = LoggerFactory.getLogger(Endpoints.class);
    static KafkaStreams streams;



//    ResponseEntity responseEntity = start();

    @ApiOperation(value = "Start a profile task ")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Task started")})
    @PostMapping(value = "/admin/start", produces = "application/json")
    public static ResponseEntity<?> start() {
        CachedSchemaRegistryClient client = new CachedSchemaRegistryClient(System.getenv().getOrDefault("SCHEMA_REGISTRY_URL", Application.SCHEMA_REGISTRY_URL), 20);

        try {
            client.getAllSubjects();
        } catch (IOException | io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException e) {
            return new ResponseEntity<>("{\"started\": \"false\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        HostInfo hostInfo = new HostInfo(System.getenv().getOrDefault("MY_HOST", "127.0.0.1"), Integer.parseInt(System.getenv().getOrDefault("MY_PORT", "8080")));


//        Starts the kafka streams profile app
        streams = MetricsApp.start(hostInfo);

        return new ResponseEntity<>("{\"started\": \"true\"}", HttpStatus.OK);
    }

    @ApiOperation(value = "Get a profile query ")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Got result")})
    @GetMapping(value = "/admin/getKSMetrics", produces = "application/json")
    public ResponseEntity<?> getKSMetrics() throws RestClientException {
        if (streams == null) {
            return new ResponseEntity<>("{\"error\": \"not running\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(streams.metrics(), HttpStatus.OK);
    }

    @ApiOperation(value = "Get the metrics")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Got result")})
    @GetMapping(value = "/getMetrics", produces = "application/json")
    public ResponseEntity<?> getConsumerInfo(@Valid Query query) throws InterruptedException, RestClientException {
        if (streams == null) {
            return new ResponseEntity<>("{\"error\": \"not running\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return MetricsApp.getMetrics(streams, query);
    }

    @ApiOperation(value = "Get health")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Got result")})
    @GetMapping(value = "/health", produces = "application/json")
    public int health() {
        logger.info("checking health");
        if (streams!= null && streams.state().isRunningOrRebalancing()) {
            return 200;
        } else {
            return 404;
        }
    }


    @ApiOperation(value = "Get all tasks ", response = String.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Status of all tasks found")})
    @GetMapping(value = "/status", produces = "application/json")
    public ResponseEntity<String> status() {
        return new ResponseEntity<>("{\"workerName\":\""+System.getenv().get("MY_POD_NAME") + "\","+"\"status\":\"" + streams.state() + "\"}", HttpStatus.OK);
    }
}