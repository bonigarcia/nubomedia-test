FROM nubomedia/apps-baseimage:v1

MAINTAINER Nubomedia

RUN mkdir /tmp/demo
ADD kurento-tree-demo-embed-6.4.1-SNAPSHOT.jar /tmp/demo/
ADD keystore.jks /

ENTRYPOINT java -jar /tmp/demo/kurento-tree-demo-embed-6.4.1-SNAPSHOT.jar
