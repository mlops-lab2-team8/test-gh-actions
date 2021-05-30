FROM openjdk:8-jdk-alpine
LABEL maintainer="prohan@whiteklay.in"
RUN apk add maven
COPY . ./app
RUN mvn clean package -f app/pom.xml
ENTRYPOINT ["java","-jar","/app/target/JobServer-1.0-SNAPSHOT.jar"]