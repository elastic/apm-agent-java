FROM alpine:3.9
RUN mkdir /usr/agent
ARG JAR_FILE
COPY ${JAR_FILE} /usr/agent/elastic-apm-agent.jar
