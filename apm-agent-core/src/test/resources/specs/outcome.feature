Feature: Outcome

  # ---- general span & transaction

  Scenario: Default span outcome is unknown
    Given an active span
    Then span outcome is 'unknown'

  Scenario: Default transaction outcome is unknown
    Given an active transaction
    Then transaction outcome is 'unknown'

  # ---- user set outcome

  Scenario: User set outcome on span has priority over instrumentation
    Given an active span
    And user sets span outcome to 'failure'
    And span terminates with outcome 'success'
    Then span outcome is 'failure'

  Scenario: User set outcome on transaction has priority over instrumentation
    Given an active transaction
    And user sets transaction outcome to 'unknown'
    And transaction terminates with outcome 'failure'
    Then transaction outcome is 'unknown'

  # ---- DB spans

  Scenario: DB span without error
    Given an active DB span without error
    Then span outcome is 'success'

  Scenario: DB span with error
    Given an active DB span with error
    Then span outcome is 'failure'

  # ---- HTTP

  @http
  Scenario Outline: HTTP transaction and span outcome
    Given an HTTP transaction with <status> response code
    Then transaction outcome is "<server>"
    Given an HTTP span with <status> response code
    Then span outcome is "<client>"
    Examples:
      | status | client  | server  |
      | 100    | success | success |
      | 200    | success | success |
      | 300    | success | success |
      | 400    | failure | success |
      | 404    | failure | success |
      | 500    | failure | failure |
      | -1     | failure | failure |
      # last row with negative status represents the case where the status is not available
      # for example when an exception/error is thrown without status (IO error, redirect loop, ...)

  # ---- gRPC

  # reference spec : https://github.com/grpc/grpc/blob/master/doc/statuscodes.md
  # the following statuses are not used by gRPC client & server
  # thus they should be considered as client-side errors
  # - INVALID_ARGUMENT
  # - NOT_FOUND
  # - ALREADY_EXISTS
  # - PERMISSION_DENIED
  # - FAILED_PRECONDITION
  # - ABORTED
  # - OUT_OF_RANGE
  # - DATA_LOSS
  # - UNAUTHENTICATED

  @grpc
  Scenario Outline: gRPC transaction and span outcome
    Given a gRPC transaction with '<status>' status
    Then transaction outcome is "<server>"
    Given a gRPC span with '<status>' status
    Then span outcome is "<client>"
    Examples:
      | status              | client  | server  |
      | OK                  | success | success |
      | CANCELLED           | failure | failure |
      | UNKNOWN             | failure | failure |
      | INVALID_ARGUMENT    | failure | success |
      | DEADLINE_EXCEEDED   | failure | failure |
      | NOT_FOUND           | failure | success |
      | ALREADY_EXISTS      | failure | success |
      | PERMISSION_DENIED   | failure | success |
      | RESOURCE_EXHAUSTED  | failure | failure |
      | FAILED_PRECONDITION | failure | success |
      | ABORTED             | failure | success |
      | OUT_OF_RANGE        | failure | success |
      | UNIMPLEMENTED       | failure | failure |
      | INTERNAL            | failure | failure |
      | UNAVAILABLE         | failure | failure |
      | DATA_LOSS           | failure | success |
      | UNAUTHENTICATED     | failure | success |
      | n/a                 | failure | failure |
    # last row with 'n/a' status represents the case where status is not available
