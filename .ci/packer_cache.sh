#!/usr/bin/env bash
source /usr/local/bin/bash_standard_lib.sh

JAVA_HOME=$HOME/.java/java10 ./mvnw clean package -DskipTests -Dmaven.javadoc.skip=true dependency:go-offline --fail-never

declare -a docker_images=(
    "registry.access.redhat.com/jboss-eap-6/eap64-openshift"
    "registry.access.redhat.com/jboss-eap-7/eap70-openshift"
    "registry.access.redhat.com/jboss-eap-7/eap71-openshift"
    "registry.access.redhat.com/jboss-eap-7/eap72-openshift"
    "jetty:9.2"
    "jetty:9.3"
    "jetty:9.4"
    "payara/server-web:4.181"
    "payara/server-web:5.182"
    "tomcat:7-jre7-slim"
    "tomcat:8.5.0-jre8"
    "tomcat:8.5-jre8-slim"
    "tomcat:9-jre9-slim"
    "tomcat:9-jre10-slim"
    "tomcat:9-jre11-slim"
    "websphere-liberty:8.5.5"
    "websphere-liberty:webProfile7"
    "jboss/wildfly:8.2.1.Final"
    "jboss/wildfly:9.0.0.Final"
    "jboss/wildfly:10.0.0.Final"
    "jboss/wildfly:11.0.0.Final"
    "jboss/wildfly:12.0.0.Final"
    "jboss/wildfly:13.0.0.Final"
    "jboss/wildfly:14.0.0.Final"
    "jboss/wildfly:15.0.0.Final"
    "jboss/wildfly:16.0.0.Final"
    "mysql:5"
    "postgresql:9"
    "postgresql:10"
    "mariadb:10"
    "sqlserver:2017-CU12"
    "jamesdbloom/mockserver:mockserver-5.4.1"
    "alpine/socat:latest"
)

# Pull all the required docker images
for image in "${docker_images[@]}"
do
  docker pull $image
done

(retry 2 docker pull docker.elastic.co/observability-ci/weblogic:12.2.1.3-dev) \
  && docker tag docker.elastic.co/observability-ci/weblogic:12.2.1.3-dev store/oracle/weblogic:12.2.1.3-dev
