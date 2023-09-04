1. wrap propagate for all PropagatedContext: return code that:
 - propagate() : find an elastic context in a current context; if found - activate transaction
 - return scope that de-activate transaction on scope close
2. intercept the runWithFilters creation to try-use with a PropagatedContext containing elastic transaction
