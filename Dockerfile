# Pin to Alpine 3.19.1
# For a complete list of hashes, see:
# https://github.com/docker-library/repo-info/tree/master/repos/alpine/remote
FROM alpine@sha256:4b7ce07002c69e8f3d704a9c5d6fd3053be500b7f1c69fc0d80990c2ad8dd412
RUN mkdir /usr/agent
ARG JAR_FILE
ARG HANDLER_FILE
COPY ${JAR_FILE} /usr/agent/elastic-apm-agent.jar
COPY ${HANDLER_FILE} /usr/agent/elastic-apm-handler
RUN chmod +x /usr/agent/elastic-apm-handler
RUN ln -s /usr/agent/elastic-apm-agent.jar /javaagent.jar
