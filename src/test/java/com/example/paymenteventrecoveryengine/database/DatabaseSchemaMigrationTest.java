
        package com.example.paymenteventrecoveryengine.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The configured test database must be an empty, dedicated PostgreSQL database
 * on its first run. Application startup runs Flyway and Hibernate validates
 * the mapped entities against the resulting schema before this test executes.
 */
@SpringBootTest
class DatabaseSchemaMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void freshDatabaseMigrationCreatesSchemaRequiredByEntities() {
        assertThat(flyway.info().applied())
                .anySatisfy(migration -> {
                    assertThat(migration.getVersion().getVersion()).isEqualTo("1");
                    assertThat(migration.getDescription()).isEqualTo("create payment events");
                    assertThat(migration.getState().isApplied()).isTrue();
                });

        assertThat(tableExists("payment_events")).isTrue();
        assertThat(tableExists("audit_logs")).isTrue();

        // Payment events columns
        assertColumn("payment_events", "id", "bigint", "NO", null, 64, 0);
        assertColumn("payment_events", "payment_id", "character varying", "NO", 255, null, null);
        assertColumn("payment_events", "order_id", "character varying", "NO", 255, null, null);
        assertColumn("payment_events", "amount", "numeric", "NO", null, 19, 2);
        assertColumn("payment_events", "currency", "character varying", "NO", 3, null, null);
        assertColumn("payment_events", "retry_count", "integer", "NO", null, 32, 0);
        assertColumn("payment_events", "status", "character varying", "NO", 50, null, null);
        assertColumn("payment_events", "failure_reason", "character varying", "YES", 255, null, null);
        assertColumn("payment_events", "next_retry_at", "timestamp without time zone", "YES", null, null, null);
        assertColumn("payment_events", "created_at", "timestamp without time zone", "YES", null, null, null);
        assertColumn("payment_events", "updated_at", "timestamp without time zone", "YES", null, null, null);

        // Audit logs columns
        assertColumn("audit_logs", "id", "bigint", "NO", null, 64, 0);
        assertColumn("audit_logs", "payment_id", "character varying", "YES", 255, null, null);
        assertColumn("audit_logs", "action", "character varying", "YES", 255, null, null);
        assertColumn("audit_logs", "message", "character varying", "YES", 255, null, null);
        assertColumn("audit_logs", "created_at", "timestamp without time zone", "YES", null, null, null);

        // Primary keys and identity columns
        assertPrimaryKeyOnId("payment_events");
        assertPrimaryKeyOnId("audit_logs");
        assertIdentityColumn("payment_events", "id");
        assertIdentityColumn("audit_logs", "id");

        // Constraints and indexes
        assertThat(indexExists("payment_events", "uk_payment_events_payment_id")).isTrue();
        assertThat(indexExists("payment_events", "idx_payment_events_status_next_retry_at")).isTrue();
        assertThat(indexExists("audit_logs", "idx_audit_logs_payment_id_created_at")).isTrue();
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """, Integer.class, tableName);

        return count != null && count == 1;
    }

    private void assertColumn(
            String tableName,
            String columnName,
            String dataType,
            String nullable,
            Integer characterMaximumLength,
            Integer numericPrecision,
            Integer numericScale) {

        ColumnMetadata column = jdbcTemplate.queryForObject("""
                SELECT data_type, is_nullable, character_maximum_length,
                       numeric_precision, numeric_scale
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """, (rs, rowNum) -> new ColumnMetadata(
                rs.getString("data_type"),
                rs.getString("is_nullable"),
                (Integer) rs.getObject("character_maximum_length"),
                (Integer) rs.getObject("numeric_precision"),
                (Integer) rs.getObject("numeric_scale")
        ), tableName, columnName);

        assertThat(column.dataType()).isEqualTo(dataType);
        assertThat(column.nullable()).isEqualTo(nullable);
        assertThat(column.characterMaximumLength()).isEqualTo(characterMaximumLength);
        assertThat(column.numericPrecision()).isEqualTo(numericPrecision);
        assertThat(column.numericScale()).isEqualTo(numericScale);
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename = ?
                  AND indexname = ?
                """, Integer.class, tableName, indexName);

        return count != null && count == 1;
    }

    private void assertPrimaryKeyOnId(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.key_column_usage
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = 'id'
                  AND position_in_unique_constraint IS NULL
                  AND constraint_name IN (
                      SELECT constraint_name
                      FROM information_schema.table_constraints
                      WHERE table_schema = current_schema()
                        AND table_name = ?
                        AND constraint_type = 'PRIMARY KEY'
                  )
                """, Integer.class, tableName, tableName);

        assertThat(count).isEqualTo(1);
    }

    private void assertIdentityColumn(String tableName, String columnName) {
        String identity = jdbcTemplate.queryForObject("""
                SELECT is_identity
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """, String.class, tableName, columnName);

        assertThat(identity).isEqualTo("YES");
    }

    private record ColumnMetadata(
            String dataType,
            String nullable,
            Integer characterMaximumLength,
            Integer numericPrecision,
            Integer numericScale) {
    }
}