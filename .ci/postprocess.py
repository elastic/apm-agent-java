#!/usr/bin/env python

from __future__ import print_function

import argparse
import collections
import json
import os
import platform
import re
import subprocess
import time


def cpu_model():
    cmd = "lscpu | grep \"Model name\" | awk '{for(i=3;i<=NF;i++){printf \"%s \", $i}; printf \"\\n\"}'"
    try:
        return subprocess.check_output(cmd, shell=True).strip()
    except subprocess.CalledProcessError:
        return "unknown"


def os_name():
    return platform.system()


def os_version():
    return platform.release()


def jdk_version():
    cmd = "java -version 2>&1 | grep \"java version\" | awk '{ print substr($3, 2, length($3) - 2) }'"
    return subprocess.check_output(cmd, shell=True).strip()


def src_revision():
    cmd = "git -C %s rev-parse --short HEAD" % os.getenv("WORKSPACE")
    try:
        return subprocess.check_output(cmd, shell=True).strip()
    except subprocess.CalledProcessError:
        return "unknown"


def commit_message():
    cmd = "git -C %s log --format=%%s -n 1 HEAD" % os.getenv("WORKSPACE")
    try:
        return subprocess.check_output(cmd, shell=True).strip()
    except subprocess.CalledProcessError:
        return "unknown"


def flatten_multi_valued_param(k, v):
    if "|" in v:
        keys = [key[0].lower() + key[1:] for key in re.findall(r'[A-Z]?[a-z]+|[A-Z]+(?=[A-Z]|$)', k)]
        values = [val.strip() for val in v.split("|")]

        res = collections.OrderedDict()
        for kk, vv in zip(keys, values):
            res[kk] = vv
        return res
    else:
        return {k: v}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input")
    parser.add_argument("--output")
    parser.add_argument("--timestamp", type=int)

    args = parser.parse_args()
    benchmarks = json.loads(open(args.input).read())
    meta = {
        # 'cpu_model': cpu_model(),
        'os_name': os_name(),
        'os_version': os_version(),
        'jdk_version': jdk_version(),
        'revision': src_revision(),
        'commit_message': commit_message(),
        'executed_at': int(time.time())
    }
    with open(args.output, "w") as out:
        for benchmark in benchmarks:
            benchmark['@timestamp'] = args.timestamp
            benchmark['meta'] = meta
            deleteRawData(benchmark['primaryMetric'])
            for metricName in benchmark['secondaryMetrics']:
                deleteRawData(benchmark['secondaryMetrics'][metricName])
            print('{ "index" : { "_index" : "microbenchmarks", "_type" : "_doc" } }', file=out)
            json.dump(benchmark, out)
            print('', file=out)


def deleteRawData(dict):
    if 'rawData' in dict:
        del dict['rawData']
    if 'rawDataHistogram' in dict:
        del dict['rawDataHistogram']


if __name__ == "__main__":
    main()
