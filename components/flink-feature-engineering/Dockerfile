FROM centos:centos7 as sbt

# This will fix the `Fully-qualified class name does not match` issue.
# Uncomment the following two lines and rebuild the image.
#
# ENV LANG en_US.UTF-8
# ENV LC_ALL en_US.UTF-8

RUN yum update -y && \
    yum install -y sudo && \
    yum install -y java-11-openjdk-devel && \
    curl https://bintray.com/sbt/rpm/rpm | sudo tee /etc/yum.repos.d/bintray-sbt-rpm.repo && \
    sudo yum install -y sbt && \
    yum clean all && \
    mkdir -p /test

COPY src /test/src
COPY project /test/project
COPY build.sbt /test/

RUN (cd /test/; sbt assembly)


FROM flink:1.13.0-scala_2.12-java11
RUN mkdir -p $FLINK_HOME/usrlib
COPY --from=sbt /test/target/scala-2.12/flink-feature-engineering-assembly-0.1-SNAPSHOT.jar $FLINK_HOME/usrlib/my-flink-job.jar