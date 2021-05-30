FROM adoptopenjdk/openjdk8:alpine-slim as datagen
RUN apk --no-cache add curl
RUN apk add maven
COPY kafka-connect-datagen ./app
RUN mvn clean package -f app/pom.xml


# FROM confluentinc/cp-kafka-connect:6.1.0
FROM mmolimar/kafka-connect-fs:1.3.0


ENV CONNECT_PLUGIN_PATH='usr/share/java,/usr/share/confluent-hub-components'

RUN confluent-hub install --no-prompt confluentinc/kafka-connect-s3:latest

RUN confluent-hub install --no-prompt confluentinc/kafka-connect-jdbc:latest

RUN confluent-hub install --no-prompt confluentinc/kafka-connect-elasticsearch:latest

COPY --from=datagen /app/target/components/packages/confluentinc-kafka-connect-datagen-0.5.0-SNAPSHOT/confluentinc-kafka-connect-datagen-0.5.0-SNAPSHOT/lib /usr/share/confluent-hub-components/kafka-connect-datagen

VOLUME /tmp

ENTRYPOINT /etc/confluent/docker/run
