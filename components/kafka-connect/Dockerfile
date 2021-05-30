


# FROM confluentinc/cp-kafka-connect:6.1.0
FROM mmolimar/kafka-connect-fs:1.3.0


ENV CONNECT_PLUGIN_PATH='usr/share/java,/usr/share/confluent-hub-components'

RUN confluent-hub install --no-prompt confluentinc/kafka-connect-s3:latest

RUN confluent-hub install --no-prompt confluentinc/kafka-connect-jdbc:latest

RUN confluent-hub install --no-prompt confluentinc/kafka-connect-elasticsearch:latest



VOLUME /tmp

ENTRYPOINT /etc/confluent/docker/run
