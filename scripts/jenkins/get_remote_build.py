#!/usr/bin/env python

"""
This script retrieves the latest successful build from
apm-ci.elastic.co and returns the commit hash
"""
import os
from jenkinsapi.jenkins import Jenkins

def getSCMInfroFromLatestGoodBuild(url, jobName, username=None, password=None):
    J = Jenkins(url, username, password)
    job = J[jobName]
    lgb = job.get_last_good_build()
    return lgb.get_revision()

def write_last(tmp_file, hash):
    with open(tmp_file, 'w+') as f_:
        f_.write(hash)

def read_hash(tmp_file):
    if not os.path.exists(tmp_file):
        return ''
    with open(tmp_file, 'r') as f_:
        git_hash = f_.readline()
    return git_hash

if __name__ == '__main__':
    tmp_file = '/tmp/apm-ci-jenkins-last-hash'

    last_hash = read_hash(tmp_file)
    cur_hash = getSCMInfoFromLatestGoodBuild("http://apm-ci.elastic.co", "apm-agent-go/opbeans-go-mbp/master")
    write_last(tmp_file, cur_hash)
    
    print(last_hash == cur_hash)
