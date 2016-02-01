FROM nubomedia/apps-baseimage:v1

MAINTAINER Nubomedia

RUN mkdir /tmp/magic-mirror
ADD kurento-magic-mirror-6.2.1-SNAPSHOT.zip /tmp/magic-mirror/
RUN cd /tmp/magic-mirror/ && unzip kurento-magic-mirror-6.2.1-SNAPSHOT.zip

EXPOSE 8080

ENTRYPOINT java -jar /tmp/magic-mirror/lib/kurento-magic-mirror.jar
