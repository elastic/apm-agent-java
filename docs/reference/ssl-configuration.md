---
mapped_pages:
  - https://www.elastic.co/guide/en/apm/agent/java/current/ssl-configuration.html
---

# SSL/TLS communication with APM Server [ssl-configuration]

If [SSL/TLS communication](docs-content://solutions/observability/apps/apm-agent-tls-communication.md) is enabled on the APM Server, use the `https` protocol when configuring [`server_url`](/reference/config-reporter.md#config-server-url).


## APM Server certificate authentication [ssl-server-authentication]

By default, when using HTTPS to communicate with APM Server, the agents will verify the identity of the APM Server by authenticating its certificate. It is not recommended to change this default, however it is possible through the [`verify_server_cert`](/reference/config-reporter.md#config-verify-server-cert) configuration.

If the certificate used by the APM Server is self-signed, you would need to add the same certificate (or the root certificate of the custom CA that signed it) to the JVM’s truststore. You can find which truststore is used by setting `-Djavax.net.debug=all` in the command line. Typically, it would be `$JAVA_HOME/jre/lib/security/cacerts`. For example, you can use `keytool` to import a certificate into the truststore: `keytool -import -alias apm-server-cert -file /path/to/certificate.crt -keystore /path/to/truststore`


## Agent certificate authentication [ssl-client-authentication]

If [SSL client authentication](docs-content://solutions/observability/apps/apm-agent-tls-communication.md#apm-agent-client-cert) is enabled on the APM server, the agent will be required to send a proper certificate as part of the HTTPS handshake. There is currently no configuration on the Java agent that supports that and no one straightforward option to do that that is suitable for all cases. Generally speaking, the agent will send a certificate from the JVM keystore. So, if your JVM does not use a keystore already, add the certificate file and the corresponding private key into a keystore and configure your JVM to use it:

```
  -Djavax.net.ssl.keyStore=keystore.p12
  -Djavax.net.ssl.keyStoreType=pkcs12
  -Djavax.net.ssl.keyStorePassword=<password>
```

