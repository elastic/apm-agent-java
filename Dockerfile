# Pin to Alpine 3.19.1
# For a complete list of hashes, see:
# https://github.com/docker-library/repo-info/tree/master/repos/alpine/remote
FROM alpine@sha256:c5b1261d6d3e43071626931fc004f70149baeba2c8ec672bd4f27761f8e1ad6b AS builder

RUN apk add --no-cache curl
WORKDIR /target

ARG STANDALONE_FILE
ARG RELEASE_VERSION

RUN curl -L -s -o sonatype.jar \
    "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=co.elastic.apm&a=elastic-apm-agent&v=$RELEASE_VERSION"

COPY $STANDALONE_FILE /target/standalone.jar

# Copy to the sonatype if possible
RUN [ -n "$RELEASE_VERSION" ] && mv sonatype.jar elastic-apm-agent.jar || true

# Copy to the standalone if possible
RUN [ -n "$STANDALONE_FILE" ] && mv standalone.jar elastic-apm-agent.jar || true

# Fail if file is not available
RUN ls -l elastic-apm-agent.jar

# Pin to Alpine 3.19.1
# For a complete list of hashes, see:
# https://github.com/docker-library/repo-info/tree/master/repos/alpine/remote
FROM alpine@sha256:c5b1261d6d3e43071626931fc004f70149baeba2c8ec672bd4f27761f8e1ad6b
RUN mkdir /usr/agent
ARG HANDLER_FILE
COPY --from=builder /target/elastic-apm-agent.jar /usr/agent/elastic-apm-agent.jar
COPY ${HANDLER_FILE} /usr/agent/elastic-apm-handler
RUN chmod +x /usr/agent/elastic-apm-handler
