This module contains integration tests with various application servers and servlet containers.

The main focus is not on having a big code coverage of the servlet module,
as this should be handled by the unit tests of the servlet module.
It's rather about sanity/smoke testing and to see if the agent starts up and is able to send basic HTTP transactions
and JDBC spans to a mock APM server.

The integration tests leverage [Testcontainers](https://www.testcontainers.org/) and require docker to be installed.
