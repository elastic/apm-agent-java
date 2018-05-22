# OpenTracing plugin implementation notes

## Problem space

- The actual OpenTracing bridge must not depend on the implementation
  - Users of the bridge add the bridge as a dependency to their project
  - The implementation is inside the `javaagent` jar file,
    so a transitive dependency would lead to two implementations on the class path.
- The bridge can't set the implementation (`apm-agent-core`) to provided
  - The internal API would then de-facto be a public API
  - The user should be able to update the agent without having to update the bridge dependency
  - If the `javaagent` is not set, the implementation would be missing -> `NoClassDefFoundError`
- The `apm-opentracing-plugin` sub-project can't depend on the `OpenTracing` API
  - The API would be on the class path twice, possibly with different versions
  

-> The bridge/API can't depend on the implementation and the implementation can't depend on the API/bridge as well. 

## Solution

The solution to this problem is that the `apm-opentracing` bridge is implemented as a noop implementation of the OpenTracing API,
and that the `apm-opentracing-plugin` injects the actual implementation via byte code instrumentation.
That way, there are no errors when the user does not add the `javaagent` - it just defaults to a noop implementation then.
Changes to the internal API are possible without the user having to update the bridge.
