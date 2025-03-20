---
navigation_title: "Deprecations"
---

# Elastic APM Java Agent deprecations [elastic-apm-java-agent-deprecations]
Review the deprecated functionality for your Elastic APM Java Agent version. While deprecations have no immediate impact, we strongly encourage you update your implementation after you upgrade.

To learn how to upgrade, check out [Upgrading](/reference/upgrading.md).

% ## Next version
% **Release date:** Month day, year

% ::::{dropdown} Deprecation title
% Description of the deprecation.
% For more information, check [PR #](PR link).
% **Impact**<br> Impact of deprecation.
% **Action**<br> Steps for mitigating deprecation impact.
% ::::

## 1.33.0 [elastic-apm-java-agent-1-33-0-deprecations]
**Release date:** July 8, 2022

* Deprecated [`url_groups`](/reference/config-http.md#config-url-groups) in favor of [`transaction_name_groups`](/reference/config-core.md#config-transaction-name-groups).

## 1.18.0 [elastic-apm-java-agent-1-18-0-deprecations]
**Release date:** September 8, 2020

* Deprecating `ignore_urls` config in favour of [`transaction_ignore_urls`](/reference/config-http.md#config-transaction-ignore-urls) to align with other agents, while still allowing the old config name for backward compatibility - [#1315](https://github.com/elastic/apm-agent-java/pull/1315)