FROM adoptopenjdk/openjdk8:alpine-slim
RUN apk --no-cache add curl
LABEL maintainer="prohan@whiteklay.in"
RUN apk add maven
COPY . ./app
RUN mvn clean package -f app/pom.xml
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app/target/metrics-1.0-SNAPSHOT.jar"]