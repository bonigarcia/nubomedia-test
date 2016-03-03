FROM nubomedia/apps-baseimage:v1

MAINTAINER Nubomedia

RUN mkdir /tmp/magic-mirror
ADD nubomedia-magic-mirror-6.4.1-SNAPSHOT.jar /tmp/magic-mirror/
ADD keystore.jks /

ENTRYPOINT java -jar /tmp/magic-mirror/nubomedia-magic-mirror-6.4.1-SNAPSHOT.jar
