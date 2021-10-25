@opentelemetry-bridge
Feature: OpenTelemetry bridge

  # --- Creating Elastic span or transaction from OTel span

  Scenario: Create transaction from OTel span with remote context
    Given an agent
    And OTel span is created with remote context as parent
    Then Elastic bridged object is a transaction
    Then Elastic bridged transaction has remote context as parent

  Scenario: Create root transaction from OTel span without parent
    Given an agent
    And OTel span is created without parent
    Then Elastic bridged object is a transaction
    Then Elastic bridged transaction is a root transaction

  Scenario: Create span from OTel span
    Given an agent
    And OTel span is created with local context as parent
    Then Elastic bridged object is a span
    Then Elastic bridged span has local context as parent

  # --- OTel span kind mapping for spans & transactions

  Scenario Outline: OTel span kind <kind> is transmitted as-is for spans
    Given an agent
    And an active transaction
    And OTel span is created with kind "<kind>"
    Then Elastic bridged object is a span
    Then Elastic bridged span OTel kind is "<kind>"
    Examples:
      | kind     |
      | INTERNAL |
      | SERVER   |
      | CLIENT   |
      | PRODUCER |
      | CONSUMER |

  Scenario Outline: OTel span kind <kind> is transmitted as-is for transactions
    Given an agent
    And OTel span is created with kind "<kind>"
    Then Elastic bridged object is a transaction
    Then Elastic bridged transaction OTel kind is "<kind>"
    Examples:
      | kind     |
      | INTERNAL |
      | SERVER   |
      | CLIENT   |
      | PRODUCER |
      | CONSUMER |


  # --- span type, subtype and action inference from OTel attributes

  # --- HTTP client

  # https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#http-client
  Scenario Outline: HTTP client [ <http.url> <http.scheme> <http.host> <net.peer.ip> <net.peer.name> <net.peer.port> ]
    Given an agent
    And an active transaction
    And OTel span is created with kind 'CLIENT'
    And OTel span has following attributes
      | http.url          | <http.url>      |
      | http.scheme       | <http.scheme>   |
      | http.host         | <http.host>     |
      | net.peer.ip       | <net.peer.ip>   |
      | net.peer.name     | <net.peer.name> |
      | net.peer.port     | <net.peer.port> |
    Then Elastic bridged span type is 'external'
    Then Elastic bridged span subtype is 'http'
    Then Elastic bridged span OTel attributes are copied as-is
    Then Elastic bridged span destination resource is set to "<resource>"
    Examples:
      | http.url                      | http.scheme | http.host       | net.peer.ip | net.peer.name | net.peer.port | resource             |
      | https://testing.invalid:8443/ |             |                 |             |               |               | testing.invalid:8443 |
      | https://[::1]/                |             |                 |             |               |               | [::1]:443            |
      | http://testing.invalid/       |             |                 |             |               |               | testing.invalid:80   |
      |                               | http        | testing.invalid |             |               |               | testing.invalid:80   |
      |                               | https       | testing.invalid | 127.0.0.1   |               |               | testing.invalid:443  |
      |                               | http        |                 | 127.0.0.1   |               | 81            | 127.0.0.1:81         |
      |                               | https       |                 | 127.0.0.1   |               | 445           | 127.0.0.1:445        |
      |                               | http        |                 | 127.0.0.1   | host1         | 445           | host1:445            |
      |                               | https       |                 | 127.0.0.1   | host2         | 445           | host2:445            |

  # --- DB client

  Scenario Outline: DB client [ <db.system> <net.peer.ip> <net.peer.name> <net.peer.port>]
    Given an agent
    And an active transaction
    And OTel span is created with kind 'CLIENT'
    And OTel span has following attributes
      | db.system         | <db.system>     |
      | net.peer.ip       | <net.peer.ip>   |
      | net.peer.name     | <net.peer.name> |
      | net.peer.port     | <net.peer.port> |
    Then Elastic bridged span type is 'db'
    Then Elastic bridged span subtype is "<db.system>"
    Then Elastic bridged span OTel attributes are copied as-is
    Then Elastic bridged span destination resource is set to "<resource>"
    Examples:
      | db.system       | net.peer.ip | net.peer.name | net.peer.port | resource       |
      | mysql           |             |               |               | mysql          |
      | oracle          |             | oracledb      |               | oracledb       |
      | oracle          | 127.0.0.1   |               |               | 127.0.0.1      |
      | mysql           | 127.0.0.1   | dbserver      | 3307          | dbserver:3307  |


  # --- Messaging producer (client span emitting a message)

  # --- RPC client


  # --- TODO : Messaging


