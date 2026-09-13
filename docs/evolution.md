# Technical evolution sequence

This is an illustrative January–September engineering curriculum, not a record of development performed in those months. Git commits retain their actual creation dates.

| Stage | Technical increment | Evidence expected |
| --- | --- | --- |
| January | Domain boundaries and decimal arithmetic | Unit tests for monetary invariants |
| February | Persistence and schema migration | Empty-database startup and constraints |
| March | HTTP contracts and validation | Positive/negative API cases |
| April | Identity and ownership boundaries | PKCE login and cross-identity denial |
| May | Idempotency and concurrency | Replays and simultaneous withdrawals |
| June | Outbox and audit projection | Real broker delivery and deduplication |
| July | Accessible account workspace | Keyboard/browser/accessibility tests |
| August | Resource limits and recovery | Broker interruption and security checks |
| September | Reproducible delivery | Docker build, CI, versioned contracts and documented limits |

Each increment can be explained through its invariant, failure mode, implementation tradeoff and executable test.
