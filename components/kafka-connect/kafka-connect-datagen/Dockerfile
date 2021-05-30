FROM adoptopenjdk/openjdk8:alpine-slim
RUN apk --no-cache add curl
RUN apk add maven
COPY . ./app
RUN mvn clean package -f app/pom.xml
