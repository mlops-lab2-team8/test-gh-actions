package community.mlops.models;

import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class CreateMetricsApp {
    @ApiModelProperty(notes = "Topic Name",required=true, example = "kafka_topic")
    @NotEmpty
    @NotBlank
    @NotNull
    private String topicName;


    @ApiModelProperty(notes = "Job Name",required=true, example = "kafka_topic")
    private String jobName = topicName;

    @ApiModelProperty(notes = "No of workers",required=true, example = "1")
    private String workers = "1";


    public String getWorkers() {
        return workers;
    }

    public void setWorkers(String workers) {
        this.workers = workers;
    }


    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }
}
