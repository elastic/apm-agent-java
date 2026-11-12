FROM alpine@sha256:f59abe656b692d901e184be3445e084786602108599d2a51d229c0f5a48c71bf
RUN mkdir /usr/agent
ARG JAR_FILE
ARG HANDLER_FILE
COPY ${JAR_FILE} /usr/agent/elastic-apm-agent.jar
COPY ${HANDLER_FILE} /usr/agent/elastic-apm-handler
RUN chmod +x /usr/agent/elastic-apm-handler
RUN ln -s /usr/agent/elastic-apm-agent.jar /javaagent.jar
