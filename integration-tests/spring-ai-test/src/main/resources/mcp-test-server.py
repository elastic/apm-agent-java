#!/usr/bin/env python3
# Licensed to Elasticsearch B.V. under one or more contributor license agreements.
# See the NOTICE file distributed with this work for additional information
# regarding copyright ownership. Elasticsearch B.V. licenses this file to you
# under the Apache License, Version 2.0 (the "License"); you may not use this
# file except in compliance with the License. You may obtain a copy of the
# License at http://www.apache.org/licenses/LICENSE-2.0
"""Minimal deterministic MCP stdio server for Spring AI integration tests."""

import json
import sys


TOOL = {
    "name": "echo",
    "description": "Echo a test message, optionally repeated.",
    "inputSchema": {
        "type": "object",
        "properties": {
            "message": {"type": "string"},
            "repeat": {"type": "integer", "minimum": 1, "maximum": 10, "default": 1},
        },
        "required": ["message"],
        "additionalProperties": False,
    },
}


def send(message):
    sys.stdout.write(json.dumps(message, separators=(",", ":")) + "\n")
    sys.stdout.flush()


def result(request_id, payload):
    send({"jsonrpc": "2.0", "id": request_id, "result": payload})


def error(request_id, code, message):
    send({"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}})


def handle(request):
    method = request.get("method")
    request_id = request.get("id")
    params = request.get("params") or {}
    if method == "initialize":
        result(
            request_id,
            {
                "protocolVersion": params.get("protocolVersion", "2025-11-25"),
                "capabilities": {"tools": {"listChanged": False}},
                "serverInfo": {"name": "spring-ai-integration-tools", "version": "1.0.0"},
            },
        )
    elif method == "ping":
        result(request_id, {})
    elif method == "tools/list":
        result(request_id, {"tools": [TOOL]})
    elif method == "tools/call":
        arguments = params.get("arguments") or {}
        message = arguments.get("message")
        repeat = arguments.get("repeat", 1)
        if params.get("name") != "echo" or not isinstance(message, str):
            error(request_id, -32602, "invalid echo request")
            return
        result(
            request_id,
            {
                "content": [{"type": "text", "text": " ".join([message] * repeat)}],
                "isError": False,
            },
        )
    elif request_id is not None:
        error(request_id, -32601, "method not found")


def main():
    for line in sys.stdin:
        try:
            handle(json.loads(line))
        except Exception as exception:
            print(f"MCP test server error: {exception}", file=sys.stderr, flush=True)


if __name__ == "__main__":
    main()
