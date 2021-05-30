package community.mlops.models;

public class Metrics {
    private final Double avgTime;
    private final Long count;
    private final Long sum;

    public Metrics(Long count, Long sum, Double avgTime) {
        this.avgTime = avgTime;
        this.count = count;
        this.sum = sum;
    }

    public Double getAvgTime() {
        return avgTime;
    }

    public Long getCount() {
        return count;
    }

    public Long getSum() {
        return sum;
    }
}
