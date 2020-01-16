<!--
A few suggestions about filling out this PR

1. Use a descriptive title for the PR.
2. If this pull request is work in progress, create a draft PR instead of prefixing the title with WIP.
3. Please label this PR at least one of the following labels, depending on the scope of your change:
- type:new-feature, which adds new behavior
- type:bug fix
- type:enhancement, which modifies existing behavior
- type:breaking-change
4. Remove those recommended/optional sections if you don't need them. Only "What does this PR do" and "Checklist" are mandatory.
5. Submit the pull request: Push your local changes to your forked copy of the repository and submit a pull request (https://help.github.com/articles/using-pull-requests).
6. Please be patient. We might not be able to review your code as fast as we would like to, but we'll do our best to dedicate to it the attention it deserves. Your effort is much appreciated!
-->

## What does this PR do?
<!-- _(Mandatory)_
Replace this comment with a description of what's being changed by this PR. Please explain the WHAT: A clear and concise description of what (patterns used, algorithms implemented, design architecture, message processing, etc.)
-->

## Checklist
<!-- _(Mandatory)_
List here all the items you have verified BEFORE sending this PR. Please DO NOT remove any item, striking through those that do not apply. (Just in case, strikethrough uses two tildes. ~~Scratch this.~~)
-->
- [ ] My code follows the [style guidelines of this project](CONTRIBUTING.md#java-language-formatting-guidelines)
- [ ] I have rebased my changes on top of the latest master branch
<!--
Update your local repository with the most recent code from the main repo, and rebase your branch on top of the latest master branch. We prefer your initial changes to be squashed into a single commit. Later, if we ask you to make changes, add them as separate commits. This makes them easier to review.
-->
- [ ] I have performed a self-review of my own code
- [ ] I have made corresponding changes to the documentation
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing [**unit** tests](https://github.com/elastic/apm-agent-java/blob/master/CONTRIBUTING.md#testing) pass locally with my changes
<!--
Run the test suite to make sure that nothing is broken. See https://github.com/elastic/apm-agent-java/blob/master/CONTRIBUTING.md#testing for details.
-->
- [ ] I have updated [CHANGELOG.asciidoc](CHANGELOG.asciidoc)
- [ ] I have updated [supported-technologies.asciidoc](docs/supported-technologies.asciidoc)
- [ ] Added an API method or config option? Document in which version this will be introduced
- [ ] Added an instrumentation plugin? How did you make sure that old, non-supported versions are not instrumented by accident?

## Author's Checklist
<!-- _(Recommended)_
Add a checklist of things that are required to be reviewed in order to have the PR approved
-->

## Related issues
<!-- _(Recommended)_
Link related issues below. Insert the issue link or reference after the word "Closes" if merging this should automatically close it. For more info see:
https://help.github.com/articles/closing-issues-using-keywords/
- Closes #ISSUE_ID
- Relates #ISSUE_ID
- Requires #ISSUE_ID
- Supersedes #ISSUE_ID
-->

## Use cases
<!-- _(Recommended)_
Explain here the different behaviors that this PR introduces or modifies in this project, user roles, environment configuration, etc.
If you are familiar with Gherkin test scenarios, we recommend its usage: https://cucumber.io/docs/gherkin/reference/
-->

## Screenshots
<!-- _(Optional)_
Add here screenshots about how the project will be changed after the PR is applied. They could be related to web pages, terminal, etc, or any other image you consider important to be shared with the team.
