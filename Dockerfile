FROM nubomedia/apps-baseimage:v1

MAINTAINER Nubomedia

RUN mkdir /tmp/magic-mirror
ADD kurento-magic-mirror-6.2.2-SNAPSHOT.jar /tmp/magic-mirror/
ADD keystore.jks /

EXPOSE 8080 8443 443

ENTRYPOINT java -jar /tmp/magic-mirror/kurento-magic-mirror.jar
