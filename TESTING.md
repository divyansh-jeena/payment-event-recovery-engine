# Database-backed tests

The repository tests use a dedicated PostgreSQL database. They do not use the
application's `DB_URL`, `DB_USERNAME`, or `DB_PASSWORD` settings. Configure
these test-only variables before running Maven:

- `TEST_DB_URL`: JDBC URL for the dedicated test database.
- `TEST_DB_USERNAME`: PostgreSQL test role.
- `TEST_DB_PASSWORD`: password for that test role.

For example, the database name can be `payment_recovery_test` on a local
PostgreSQL server. Create that database and role separately, then set the
variables in your shell. Do not point `TEST_DB_URL` at a development or
production database. The variables have no defaults so tests cannot silently
fall back to the application database.

On the first run, the dedicated database must be empty (no application tables
or Flyway history). Flyway applies the checked-in migrations at Spring context
startup, and Hibernate runs with `ddl-auto=validate`. The schema integration
test checks the applied migration, columns, nullability, lengths, numeric
precision/scale, unique constraint, and repository indexes. Repository tests
run against this same dedicated database; keep it separate from any database
whose contents must be preserved.

PowerShell example (fill in credentials locally; do not commit them):

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/payment_recovery_test'
$env:TEST_DB_USERNAME = '<test-role>'
$env:TEST_DB_PASSWORD = '<test-password>'
./mvnw.cmd -Dtest=DatabaseSchemaMigrationTest test
./mvnw.cmd test
```

The full suite requires a reachable PostgreSQL server and the dedicated test
database. PostgreSQL Testcontainers is not configured because the Docker daemon
was unavailable during this change.
