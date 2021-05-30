package community.mlops;

import community.mlops.models.CreateMetricsApp;
import community.mlops.models.MetricsModel;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ClientBuilder;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;

@RestController
@Api(tags = { "Endpoints" })
public class Endpoints {
    private static final Logger logger = LoggerFactory.getLogger(Endpoints.class);

    @ApiOperation(value = "Create a Metrics task ")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Task started")})
    @PostMapping(value = "/metrics", produces = "application/json")
    public ResponseEntity<?> create(@Valid @RequestBody CreateMetricsApp createMetricsApp) throws IOException {
        logger.info("got metricsModel config: "+ createMetricsApp);


        ApiClient client = ClientBuilder.cluster().build();
        client.setDebugging(true);

        Configuration.setDefaultApiClient(client);

        AppsV1Api appsV1Api = new AppsV1Api();
        CoreV1Api coreV1Api = new CoreV1Api();

        String jobName = createMetricsApp.getJobName();

        V1StatefulSet statefulSet = Metrics.createStatefulSet(createMetricsApp.getTopicName(),
                jobName, createMetricsApp.getWorkers());
        V1Service service = Metrics.createService(jobName);
        try {
            appsV1Api.createNamespacedStatefulSet("default", statefulSet, null, null, null);
            coreV1Api.createNamespacedService("default", service, null, null, null);
            return new ResponseEntity<>("{" + "\"status\":\"SUCCESS\"" + "}", HttpStatus.OK);
        }
        catch (ApiException e) {
            e.printStackTrace();
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }



    @ApiOperation(value = "Delete a metrics task ")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Task started")})
    @DeleteMapping(value = "/metrics/{jobName}", produces = "application/json")
    public ResponseEntity<?> delete(@PathVariable String jobName) throws IOException {
        ApiClient client = ClientBuilder.cluster().build();
        client.setDebugging(true);

        Configuration.setDefaultApiClient(client);

        AppsV1Api api = new AppsV1Api();
        CoreV1Api api2 = new CoreV1Api();

        ArrayList<String> hosts;
        try {
            hosts = lookup(jobName+"-service");
        } catch (UnknownHostException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        try {
            V1DeleteOptions deleteOptions = new V1DeleteOptionsBuilder()
                    .build();
            api.deleteNamespacedStatefulSet(jobName,"default", null, null,60,false, "Foreground", deleteOptions);
            api2.deleteNamespacedService(jobName+"-service","default", null, null,60,false, "Foreground", deleteOptions);
            for (int i = 0; i < hosts.size(); i++) {
                api2.deleteNamespacedPersistentVolumeClaimAsync(jobName+"-state-store-"+jobName+"-"+i, "default", null, null, 60, false, "Foreground", deleteOptions, null);
            }
            return new ResponseEntity<>("{" + "\"status\":\"SUCCESS\"" + "}", HttpStatus.OK);
        }
        catch (ApiException e) {
            e.printStackTrace();
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }

    }

    @ApiOperation(value = "Get metrics from an app")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Got result")})
    @GetMapping(value = "/metrics/{jobName}", produces = "application/json")
    public ResponseEntity<?> query(@Valid @RequestBody MetricsModel metricsModel) throws URISyntaxException, IOException {

        ArrayList<String> hosts;
        try {
            hosts = lookup(metricsModel.getJobName() + "-service");
        }
        catch (UnknownHostException e) {
            return new ResponseEntity<>("{\"errorMessage\": \"job is not running\"}", HttpStatus.BAD_REQUEST);
        }


        System.out.println("getting from host" + hosts.get(0));
        URIBuilder builder = new URIBuilder("http://" + hosts.get(0) +":" + "8080" + "/getMetrics");
        builder.setParameter("endTime", metricsModel.getEndTime())
                .setParameter("startTime", metricsModel.getStartTime());
        HttpGet get = new HttpGet(builder.build());
        HttpClient client = HttpClientBuilder.create().build();

        HttpResponse response = client.execute(get);
        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
            out.append(line);
        }
        return new ResponseEntity<>(out.toString(), HttpStatus.resolve(response.getStatusLine().getStatusCode()));

    }



    @ApiOperation(value = "Get metrics task statuses")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Got result")})
    @GetMapping(value = "/metrics/{jobName}/status", produces = "application/json")
    public ResponseEntity<?> profileStatus(@PathVariable String jobName) throws IOException {

        ArrayList<String> hosts;
        try {
            hosts = lookup(jobName+"-service");
        }
        catch (UnknownHostException e) {

            ApiClient client = ClientBuilder.cluster().build();
            client.setDebugging(true);

            Configuration.setDefaultApiClient(client);

            AppsV1Api api = new AppsV1Api();
            try {
                V1StatefulSet v1StatefulSet = api.readNamespacedStatefulSetStatus(jobName, "default",null);
                V1StatefulSetStatus v1StatefulSetStatus = v1StatefulSet.getStatus();
                return new ResponseEntity<>(v1StatefulSetStatus, HttpStatus.INTERNAL_SERVER_ERROR);
            }
            catch (ApiException e2) {
                return new ResponseEntity<>("{\"id\":\""+jobName+"\",\"errorMessage\": \"job never submitted or already deleted\"}", HttpStatus.BAD_REQUEST);
            }
        }


        try {
            JSONObject response = new JSONObject();
            response.put("id", jobName);

            JSONArray result = new JSONArray();

            for (String host:
                    hosts) {
                URIBuilder builder = new URIBuilder("http://" + host+":" + "8080" + "/status");
                HttpGet get = new HttpGet(builder.build());
                HttpClient client = HttpClientBuilder.create().build();
                HttpResponse httpResponse = client.execute(get);
                BufferedReader rd = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = rd.readLine()) != null) {
                    out.append(line);
                }
                result.put(new JSONObject(out.toString()));
            }
            response.put("workers", result);
            return new ResponseEntity<>(response.toString(), HttpStatus.OK);

        }
        catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);

        }
    }




    @ApiOperation(value = "Get profile task statuses")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Got result")})
    @GetMapping(value = "/metrics/{jobName}/KSMetrics", produces = "application/json")
    public ResponseEntity<?> flowMetrics(@PathVariable String jobName) throws IOException {
        ArrayList<String> hosts;
        try {
            hosts = lookup(jobName+"-service");
        }
        catch (UnknownHostException e) {

            ApiClient client = ClientBuilder.cluster().build();
            client.setDebugging(true);

            Configuration.setDefaultApiClient(client);

            AppsV1Api api = new AppsV1Api();
            try {
                V1StatefulSet v1StatefulSet = api.readNamespacedStatefulSetStatus(jobName, "default",null);
                V1StatefulSetStatus v1StatefulSetStatus = v1StatefulSet.getStatus();
                return new ResponseEntity<>(v1StatefulSetStatus, HttpStatus.INTERNAL_SERVER_ERROR);
            }
            catch (ApiException e2) {
                return new ResponseEntity<>("{\"id\":\""+jobName+"\",\"errorMessage\": \"job never submitted or already deleted\"}", HttpStatus.BAD_REQUEST);
            }
        }
        System.out.println("hosts: " + hosts);


        try {
            URIBuilder builder = new URIBuilder("http://" + hosts.get(0)+":" + "8080" + "/admin/getKSMetrics");
            HttpGet get = new HttpGet(builder.build());
            HttpClient client = HttpClientBuilder.create().build();
            HttpResponse httpResponse = client.execute(get);
            BufferedReader rd = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                out.append(line);
            }
            return new ResponseEntity<>(out.toString(), HttpStatus.OK);

        }
        catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);

        }
    }


    private static ArrayList<String> lookup(String host) throws UnknownHostException {
        InetAddress node;
        InetAddress[] nodes;
        ArrayList<String> hosts = new ArrayList<>();

        nodes = InetAddress.getAllByName(host);
        for (InetAddress inetAddress:
                nodes) {
            if(isHostname(host)){
                hosts.add(inetAddress.getHostAddress());
            }else {
                //this is an IP address
                hosts.add(inetAddress.getHostName());
            }
        }
        return hosts;
    } // end of lookup

    private static boolean isHostname(String host) {

        //is this a ipv6 address
        if(host.indexOf(":") != -1) return false;

        char[] ca = host.toCharArray();
        //if we see a character that is neither a digit nor a period
        //then host is probably a hostname
        for(int i=0; i<ca.length; i++){
            if(!Character.isDigit(ca[i])){
                if(ca[i] != '.') return true;
            }
        }
        //Everything was either a digit or a period
        // so host looks like a ip4v address in dotted quad format

        return false;
    }//end ishostname
}