## Release checker

This is an application to verify whether or not a given release number is present in the
published release notes for a given project in the Elastic ecosystem.

### Requirements

Requirements are a supported version of Python and the following Python libraries:

* (bs4)[https://www.crummy.com/software/BeautifulSoup]
* (requests)[https://2.python-requests.org]
* (rich)[https://github.com/willmcgugan/rich]

To quickly install all requirements, use `pip install -r requirements.txt` from the
root of this project.

### Quickstart

To run this application locally or for development, you may simply call it directly:

```
❯ ./check.py --release 1.0.0 --url https://www.elastic.co/guide/en/apm/agent/java/current/release-notes.html
Analyzing releases ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 100% 0:00:00 
👍 Release found
```

The `--release` flag is used to indicate the release that you wish to verify publication for
and the `--url` flag is used to specify the release-notes page for the project in question.

### Automation

While this script does provide output to standard out regarding the success or failure of the query,
the best way to use it in automated pipelines is to simply rely on the exit code. This script returns
`0` if the release was found and `1` if it was not.

### Docker

This project also includes a Docker file. To use it:

```
docker build . -t docs_release_check  # Run once to build the container
docker run docks_release_check --release 1.0.0 --url https://www.elastic.co/guide/en/apm/agent/java/current/release-notes.html  # Run as many times as you like after the container is built
```

### Credit

This project was built and is maintained by the Observability Robots at Elastic.
