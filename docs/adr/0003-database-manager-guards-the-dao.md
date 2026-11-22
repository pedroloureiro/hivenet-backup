# A database manager is the only class that touches the DAO

`BatchDatabaseManager` sits between the repository and Room's DAO and is the only class given the DAO. It exposes just the operations the system needs and enforces the rules the DAO cannot: a **Batch** and its files are created in one transaction, a file only moves Waiting → Copying → a final **Outcome** and a final **Outcome** never changes, and abandoned files are settled (**Not started** / **Interrupted**) in one operation. The repository only maps rows to domain models. This keeps any class from using the database in ways the system does not account for, and lets repository and use-case tests run on the JVM against a fake manager.

## Consequences

- Every manager operation must enforce a rule or be a read the system needs; a method that only forwards one DAO call is not allowed.
- The manager's rules are proven with Room tests (instrumented or Robolectric), not with fakes.
