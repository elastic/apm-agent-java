# apm-agent-core

This is the internal, or low-level API which is used to create deep integrations for things like the Servlet API.

There are no guarantees in terms of backwards compatibility.

# Core concepts

## Tracer
The tracer is the main API for creating Transactions and Spans.
When a Transaction has ended, the tracer hands it over to the reporter.

It provides access to all fields available in the APM server intake api: https://www.elastic.co/guide/en/apm/server/master/intake-api.html

## Reporter
The Reporter is responsible for sending Transactions to the APM server.

It's based on a Disruptor/ring buffer and receives finished transactions potentially from multiple threads.
The ring buffer decouples the transaction-producing threads from the co.elastic.apm.report.ReportingEventHandler,
which is single-threaded and sends the transactions to the APM server via HTTP.

The class co.elastic.apm.report.ReporterConfiguration contains all relevant configuration options for the reporter.
