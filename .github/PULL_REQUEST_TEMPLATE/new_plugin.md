---
name: New plugin
about: Add a new plugin
labels: type: new-feature
---

## What does this PR do?
<!-- _(Mandatory)_
Replace this comment with a description of what's being changed by this PR. Please explain the WHAT: A clear and concise description of what (patterns used, algorithms implemented, design architecture, message processing, etc.)
-->

## Checklist
<!-- _(Mandatory)_
List here all the items you have verified BEFORE sending this PR. Please DO NOT remove any item, striking through those that do not apply. (Just in case, strikethrough uses two tildes. ~~Scratch this.~~)
-->
- [ ] My code follows the [style guidelines of this project](CONTRIBUTING.md#java-language-formatting-guidelines)
- [ ] I have made corresponding changes to the documentation
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing [**unit** tests](https://github.com/elastic/apm-agent-java/blob/master/CONTRIBUTING.md#testing) pass locally with my changes
- [ ] I have updated [CHANGELOG.asciidoc](CHANGELOG.asciidoc)
- [ ] I have updated [supported-technologies.asciidoc](docs/supported-technologies.asciidoc)
- [ ] Added an API method or config option? Document in which version this will be introduced
- [ ] Added an instrumentation plugin? Describe wow you made sure that old, non-supported versions are not instrumented by accident.

## Author's Checklist
<!-- _(Recommended)_
Add a checklist of things that are required to be reviewed in order to have the PR approved
-->
