package community.mlops.models;

import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class MetricsModel {
    @ApiModelProperty(notes = "Start time",required=true, example = "1269576560000")
    @NotEmpty
    @NotBlank
    @NotNull
    private String startTime;

    @ApiModelProperty(notes = "End time",required=true, example = "1969576560000")
    @NotEmpty
    @NotBlank
    @NotNull
    private String endTime;


    @ApiModelProperty(notes = "Topic Name",required=true, example = "kafka_topic")
    @NotEmpty
    @NotBlank
    @NotNull
    private String topicName;


    @ApiModelProperty(notes = "Job Name",required=true, example = "kafka_topic")
    private String jobName = topicName;






    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }
}
