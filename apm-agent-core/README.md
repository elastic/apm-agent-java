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

## Lifecycle

![lifecycle](https://user-images.githubusercontent.com/2163464/43504125-64897f60-9562-11e8-8fce-b3b1553bddfe.png)

1. Start transaction recording
   - Take a pre-allocated object out of the object pool
   - If there are no objects left, we have to resort to allocations
2. Agent records data from request and response objects
   - Avoid allocations \
     For example work with reusable StringBuilders instead concatenating Strings
   - Currently the only source of allocations are Map entry set iterators \
     (to capture headers and request parameters)
3. Put recorded transaction into queue
   - This is a low latency queue, optimized for ITC (LMAX-Exchange/disruptor)
   - If full, discard and recycle transaction
4. Reporter thread takes transaction out of the queue
5. Send data to the APM server *
   - There is always an open HTTP request to the APM server
     - After a specific amount of time or after a certain amount of compressed data has been sent,
       the connection will be re-established
   - Serialization with DSL JSON (DslJsonSerializer)
   - Zero garbage, no intermediate string or byte representation
   - Serializes directly into OutputStream of the HTTP request body
   - Custom garbage free serializer for ISO timestamps
   - Output stream is wrapped to deflate JSON
   - Uses chunked-encoding to stream the data to the APM server
6. Reset the Transaction object and put it in back to object pool
   - Transaction is fully mutable
   - Resets all referenced objects
   - Immutable Objects can't be reset and create garbage
     - Favor long timestamp over immutable Instant
     - Avoid java.util.UUID
     - Favor primitive types over boxed (int vs Integer)

\* this describes the new intake v2 protocol, v1 works a bit differently
