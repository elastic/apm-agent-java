#!/usr/bin/env python

# Licensed to Elasticsearch B.V. under one or more contributor
# license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright
# ownership. Elasticsearch B.V. licenses this file to you under
# the Apache License, Version 2.0 (the "License"); you may
# not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

"""
Description:

This is an ad-hoc script used to generate the drop-down selections
for the Jenkinsfile to allow users to test against various combinations
of JDKs available in the Elastic JVM catalog which is hosted at:
https://jvm-catalog.elastic.co.

It dymamically generates a list of available JDKs and of APM Java Agent
releases. These are returns as a snippet of code which can then be pasted
directly into a Jenkinsfile. Below is an example of the kind of output
produced by this script:

    parameters {
    string(name: 'agent_version', defaultValue: 'v1.9.0', description: 'Version of agent. Should correspond to tag, e.g. `v1.9.0`.')
    string(name: 'jvm_version', defaultValue: '9.0.4', description: 'Version of JVM.')
    string(name: 'concurrent_requests', defaultValue: '100', description: 'The number of concurrent requests to test with.')
    string(name: 'duration', defaultValue: '10', description: 'Test duration in minutes. Max: 280')
    string(name: 'num_of_runs', defaultValue: '1', description: 'Number of test runs to execute.')
    }

Author: Mike Place <mike.place@elastic.co>

Maintainers: Observability Developer Productivity
<observability-robots@elastic.co>
"""

import requests
import argparse
import github
from packaging.version import parse as parse_version

CATALOG_URL = 'https://jvm-catalog.elastic.co'
# The limit of total choices must be < 256 or Jenkins will stacktrace.
# The list below represents all choices currently in the catalog
# *except* `amazon` and `jdk`.
# See https://github.com/elastic/apm-agent-java/pull/1467#discussion_r516464187
# for additional context and discussion.
SUPPORTED_JDKS = ['oracle', 'openjdk', 'adoptopenjdk', 'zulu']
MIN_JDK_VERSION = '7'

parser = argparse.ArgumentParser(description="Jenkins JDK snippet generator")
parser.add_argument(
    '--platforms',
    nargs='+',
    help='platforms help',
    type=str,
    default='linux',
    choices=['linux', 'darwin', 'windows']
    )
parser.add_argument(
    '--gh-token',
    help='GitHub token to gather supported releases',
    type=str,
    required=True
)
parser.add_argument(
    '--min-ver',
    help='Minimum version of JDK to include',
    type=str,
    required=False,
    default=MIN_JDK_VERSION
)

parsed_args = parser.parse_args()

# Gather JDKs we can support
r = requests.get(CATALOG_URL + '/jdks')
if r.status_code != 200:
    raise Exception('Error encountered trying to download JDK manifest')

supported_jdks = []

for jdk in r.json():
    arch = None
    try:
        flavor, ver, dist = jdk.split('-', 3)
    except ValueError:
        flavor, ver, dist, arch = jdk.split('-', 4)

    if dist in parsed_args.platforms and \
        flavor in SUPPORTED_JDKS and \
            parse_version(ver) >= parse_version(parsed_args.min_ver):
        if arch:
            continue
        supported_jdks.append(jdk)

# Gather releases of the agent we can support
agent_releases = []
if parsed_args.gh_token:
    gh = github.Github(login_or_token=parsed_args.gh_token)
    releases = gh.get_repo('elastic/apm-agent-java').get_releases()
    for release in releases:
        _, rel_number = release.title.split(' ')
        agent_releases.append(rel_number)

print('Paste the following into the Jenkinsfile:\n\n\n\n')

print(
    '// The following snippet is auto-generated. To update it, run the script located in .ci/load/scripts/param_gen and copy in the output',  # noqa E501
    'choice(choices: {}, name: "apm_version", description: "APM Java Agent version")'.format(agent_releases),  # noqa E501
    'choice(choices: {}, name: "jvm_version", description: "JVM")'.format(supported_jdks),  # noqa E501
    'string(name: "jvm_options", defaultValue: "", description: "Extra JVM options")',  # noqa E501
    'string(name: "concurrent_requests", defaultValue: "100", description: "The number of concurrent requests to test with")',  # noqa E501
    'string(name: "duration", defaultValue: "10", description: "Test duration in minutes. Max: 280")',  # noqa E501
    '// num_of_runs currently unsupported',
    '// string(name: "num_of_runs", defaultValue: "1", description: "Number of test runs to execute")',  # noqa E501
    'text(name: "agent_config", "defaultValue": "", description: "Custom APM Agent configuration. (WARNING: May echo to console. Do not supply sensitive data.)")',  # noqa E501
    'text(name: "locustfile", "defaultValue": "", description: "Locust load-generator plan")',  # noqa E5011
    'booleanParam(name: "local_metrics", description: "Enable local metrics collection?", defaultValue: false)',  # noqa E501
    'booleanParam(name: "ignore_application_errors", description: "Instruct the load generator to ignore non-2xx errors on exit", defaultValue: true)',  # noqa E501
    '// End script auto-generation',
    sep="\n"
)
