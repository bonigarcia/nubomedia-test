FROM nubomedia/apps-baseimage:v1

MAINTAINER Nubomedia

RUN mkdir /tmp/magic-mirror
ADD nubomedia-magic-mirror-1.0.0.jar /tmp/magic-mirror/
ADD keystore.jks /

EXPOSE 8080 8443 443

ENTRYPOINT java -jar /tmp/magic-mirror/nubomedia-magic-mirror-1.0.0.jar
