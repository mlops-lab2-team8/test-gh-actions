package community.mlops;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

public class Metrics {
    static V1StatefulSet createStatefulSet(String topicName, String jobName, String workers) {
        String stateStorePath = "data/kafkaStreams";
        V1EnvVar BOOTSTRAP_SERVERS = new V1EnvVarBuilder()
                .withName("BOOTSTRAP_SERVERS")
                .withValue(System.getenv("BOOTSTRAP_SERVERS"))
                .build();

        V1EnvVar SCHEMA_REGISTRY_URL = new V1EnvVarBuilder()
                .withName("SCHEMA_REGISTRY_URL")
                .withValue(System.getenv("SCHEMA_REGISTRY_URL"))
                .build();

        V1EnvVar JOB_NAME = new V1EnvVarBuilder()
                .withName("JOB_NAME")
                .withValue(jobName)
                .build();

        V1EnvVar SOURCE_TOPIC = new V1EnvVarBuilder()
                .withName("SOURCE_TOPIC")
                .withValue(topicName)
                .build();


        V1EnvVar MY_HOST = new V1EnvVarBuilder()
                .withName("MY_HOST")
                .withNewValueFrom()
                .withNewFieldRef()
                .withFieldPath("status.podIP")
                .endFieldRef()
                .endValueFrom()
                .build();

        V1EnvVar MY_POD_NAME = new V1EnvVarBuilder()
                .withName("MY_POD_NAME")
                .withNewValueFrom()
                .withNewFieldRef()
                .withFieldPath("metadata.name")
                .endFieldRef()
                .endValueFrom()
                .build();

        ArrayList<V1EnvVar> envs = new ArrayList();

        envs.add(BOOTSTRAP_SERVERS);
        envs.add(SCHEMA_REGISTRY_URL);
        envs.add(JOB_NAME);
        envs.add(SOURCE_TOPIC);
        envs.add(MY_HOST);
        envs.add(MY_POD_NAME);


        V1VolumeMount volumeMount = new V1VolumeMountBuilder()
                .withName(jobName + "-state-store")
                .withMountPath(stateStorePath)
                .build();

        V1Probe livenessProbe = new V1Probe()
                .httpGet(new V1HTTPGetAction().path("/health").port(new IntOrString(8080)))
                .initialDelaySeconds(300)
                .periodSeconds(20);

        V1Probe readinessProbe = new V1Probe()
                .httpGet(new V1HTTPGetAction().path("/health").port(new IntOrString(8080)))
                .initialDelaySeconds(30)
                .periodSeconds(20);

        V1Container container = new V1ContainerBuilder()
                .withName("metrics-kafka-streams")
                .withImage(System.getenv("METRICS_APP_IMAGE_WITH_TAG"))
                .withEnv(envs)
                .withVolumeMounts(volumeMount)
                .withImagePullPolicy("IfNotPresent")
                .withLivenessProbe(livenessProbe)
                .withReadinessProbe(readinessProbe)
                .build();

        HashMap<String, String> labelMap = new HashMap<>();
        labelMap.put("app", "izac");
        labelMap.put("id", jobName);
        labelMap.put("job", "metrics");


        V1PersistentVolumeClaim persistentVolumeClaim = new V1PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withLabels(labelMap)
                .withName(jobName + "-state-store")
                .endMetadata()
                .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withNewResources()
                .withRequests(new HashMap<String, Quantity>() {{
                    put("storage", new Quantity(new BigDecimal("1000000000"), Quantity.Format.BINARY_SI));
                }})
                .endResources()
                .endSpec()
                .build();


        V1StatefulSet statefulSet = new V1StatefulSetBuilder()
                .withNewMetadata()
                .withName(jobName)
                .endMetadata()
                .withNewSpec()
                .withServiceName(jobName + "-service")
                .withReplicas(Integer.parseInt(workers))
                .withNewSelector()
                .withMatchLabels(new HashMap<String, String>() {{
                    put("id", jobName);
                    put("job", "metrics");
                    put("app", "izac");
                }})
                .endSelector()
                .withPodManagementPolicy("Parallel")
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(new HashMap<String, String>() {{
                    put("id", jobName);
                    put("job", "metrics");
                    put("app", "izac");
                }})
                .endMetadata()
                .withNewSpec()
                .withContainers(container)
                .endSpec()
                .endTemplate()
                .withVolumeClaimTemplates(persistentVolumeClaim)
                .endSpec()
                .build();


        return statefulSet;
    }

    static V1Service createService(String jobName) {
        return new V1ServiceBuilder()
                .withNewMetadata()
                .withName(jobName+"-service")
                .withLabels(new HashMap<String, String>()
                {{
                    put("id", jobName);
                    put("job", "metrics");
                    put("app", "izac");
                }})
                .endMetadata()
                .withNewSpec()
                .withClusterIP("None")
                .withSelector(new HashMap<String, String>()
                {{
                    put("id", jobName);
                    put("job", "metrics");
                    put("app", "izac");
                }})
                .endSpec()
                .build();
    }
}
