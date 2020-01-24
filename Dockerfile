# Pin to Alpine 3.9
# For a complete list of hashes, see:
# https://github.com/docker-library/repo-info/tree/master/repos/alpine/remote
FROM alpine@sha256:115731bab0862031b44766733890091c17924f9b7781b79997f5f163be262178
RUN mkdir /usr/agent
ARG JAR_FILE
COPY ${JAR_FILE} /usr/agent/elastic-apm-agent.jar
