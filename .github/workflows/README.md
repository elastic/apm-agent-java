## CI/CD

There are 4 main stages that run on GitHub actions:

* Build
* Unit Test
* Integration Test
* Release

There are some other stages that run for every push on the main branches:

* [Microbenchmark](./microbenchmark.yml)
* [Snapshoty](./snapshoty.yml)
* [Sync-branches](./sync-branches.yml)

### Scenarios

* Tests should be triggered on branch, tag and PR basis.
* Commits that are only affecting the docs files should not trigger any test or similar stages that are not required.
* Automated release in the CI gets triggered through a GitHub workflow.
* **This is not the case yet**, but if Github secrets are required then Pull Requests from forked repositories won't run any build accessing those secrets. If needed, then create a feature branch (opened directly on the upstream project).

### How to interact with the CI?

#### On a PR basis

Once a PR has been opened then there are two different ways you can trigger builds in the CI:

1. Git commit based
1. UI based, any Elasticians can force a build through the GitHub UI

#### Branches

Every time there is a merge to main or any branches the whole workflow will compile and test on Linux and Windows.

### Release process

To release a new version of apm-agent-java, you must use the two GitHub Workflows.
Trigger the `Pre Release` workflow targeting the release version.
After merging the PRs created by the first workflow, you can trigger the `Release` workflow targeting the release version.
It runs then a Buildkite pipeline in charge of generating and publishing the artifacts,
for further details please go to [the buildkite folder](../../.buildkite/README.md).
Finally, merge the PRs created to bump version for the next iteration.

The tag release follows the naming convention: `v.<major>.<minor>.<patch>`, where `<major>`, `<minor>` and `<patch>`.

### OpenTelemetry

There is a GitHub workflow in charge to populate what the workflow run in terms of jobs and steps. Those details can be seen in [here](https://ela.st/oblt-ci-cd-stats) (**NOTE**: only available for Elasticians).

## Bump automation

[updatecli](https://www.updatecli.io/) is the tool we use to automatically update the specs
the [APM agents](./updatecli.yml) use.
