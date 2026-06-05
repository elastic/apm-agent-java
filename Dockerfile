# Pin to Alpine 3.23.4 linux/amd64
# For a complete list of hashes, see:
# https://github.com/docker-library/repo-info/tree/master/repos/alpine/remote
FROM alpine@sha256:4d889c14e7d5a73929ab00be2ef8ff22437e7cbc545931e52554a7b00e123d8b
RUN mkdir /usr/agent
ARG JAR_FILE
ARG HANDLER_FILE
COPY ${JAR_FILE} /usr/agent/elastic-apm-agent.jar
COPY ${HANDLER_FILE} /usr/agent/elastic-apm-handler
RUN chmod +x /usr/agent/elastic-apm-handler
RUN ln -s /usr/agent/elastic-apm-agent.jar /javaagent.jar
