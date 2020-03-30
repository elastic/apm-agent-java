#!/usr/bin/env python

"""
This script retrieves the latest successful build from
apm-ci.elastic.co and returns the commit hash
"""
import os
import time
from jenkinsapi.jenkins import Jenkins

INITIAL_RETRY_INTERVAL = 5

def getSCMInfoFromLatestGoodBuild(url, jobName, username=None, password=None):
    J = Jenkins(url, username, password)
    job = J[jobName]
    lgb = job.get_last_good_build()
    return lgb.get_revision()

def write_last(tmp_file, hash):
    with open(tmp_file, 'w+') as f_:
        f_.write(hash)

def read_hash(tmp_file):
    if not os.path.exists(tmp_file):
        ## We don't have a previous hash.
        return ''
    with open(tmp_file, 'r') as f_:
        git_hash = f_.readline()
    return git_hash

def exponential_sleep(interval):
    time.sleep(interval)
    return interval**2

if __name__ == '__main__':
    tmp_file = '/tmp/apm-ci-jenkins-last-hash'

    previous_hash = read_hash(tmp_file)

    interval = INITIAL_RETRY_INTERVAL
    cur_hash = ''
    for i in range(1):
        ## Exponential backoff of 5, 25, 625 seconds. ~11 minutes total.
        try:
            cur_hash = getSCMInfoFromLatestGoodBuild("http://apm-ci.elastic.co", "apm-agent-java/apm-agent-java-mbp/master")
        except Exception:
            interval = exponential_sleep(interval)
            continue
        if not len(cur_hash):
            interval = exponential_sleep(interval)
            continue
        break

    if not len(cur_hash):
        raise Exception("Could not retreive latest good build from http://apm-ci.elastic.co/")

    write_last(tmp_file, cur_hash)

    if previous_hash == '':
        # This is the first run on the temp file is gone, start by writing out the file
        # and waiting for the next change to occur. (Can be overriden via
        # pipline parameter to force.)
        print(True)
    
    print(previous_hash == cur_hash)
