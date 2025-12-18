# Pin to Alpine 3.19.1
# For a complete list of hashes, see:
# https://github.com/docker-library/repo-info/tree/master/repos/alpine/remote
FROM alpine@sha256:865b95f46d98cf867a156fe4a135ad3fe50d2056aa3f25ed31662dff6da4eb62
RUN mkdir /usr/agent
ARG JAR_FILE
ARG HANDLER_FILE
COPY ${JAR_FILE} /usr/agent/elastic-apm-agent.jar
COPY ${HANDLER_FILE} /usr/agent/elastic-apm-handler
RUN chmod +x /usr/agent/elastic-apm-handler
RUN ln -s /usr/agent/elastic-apm-agent.jar /javaagent.jar
