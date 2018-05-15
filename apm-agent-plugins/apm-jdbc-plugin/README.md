# JDBC plugin

This plugin creates spans for JDBC queries.

## Implementation Notes:

The JDBC API itself is loaded by the bootstrap class loader.

The implementations are however mostly loaded by
 * The application server class loader (or a module class loader), if the server bundles the implementation
 * The web app class loader, if the application bundles the JDBC driver
 * The `SystemClassLoader`, when starting a simple `main` method Java program

As a consequence,
there are not lots of class loader issues,
as the implementations are loaded by a child of the `SystemClassLoader` or the `SystemClassLoader` itself,
which also loads the agent code.
But as we don't need to refer to implementation specific classes,
but only the JDBC API,
which is loaded by the bootstrap class loader,
which is a parent class loader of both the agent and the JDBC implementation,
we can fully reference it even in helper classes.

