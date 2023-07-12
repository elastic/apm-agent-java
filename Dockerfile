# Pin to Alpine 3.9
# For a complete list of hashes, see:
# https://github.com/docker-library/repo-info/tree/master/repos/alpine/remote
FROM alpine@sha256:82d1e9d7ed48a7523bdebc18cf6290bdb97b82302a8a9c27d4fe885949ea94d1
RUN mkdir /usr/agent
ARG JAR_FILE
ARG HANDLER_FILE
COPY ${JAR_FILE} /usr/agent/elastic-apm-agent.jar
COPY ${HANDLER_FILE} /usr/agent/elastic-apm-handler
RUN chmod +x /usr/agent/elastic-apm-handler
