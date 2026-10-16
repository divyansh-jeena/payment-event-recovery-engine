
package com.example.paymenteventrecoveryengine.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The configured test database must be a dedicated PostgreSQL database.
 * On its first run, it should be empty so Flyway can create the schema.
 *
 * Application startup runs Flyway and Hibernate validates the mapped
 * entities against the resulting schema before this test executes.
 */
@SpringBootTest
class DatabaseSchemaMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void freshDatabaseMigrationCreatesSchemaRequiredByEntities() {

        // Verify that migration version 1 was successfully applied.
        assertThat(flyway.info().applied())
                .anySatisfy(migration -> {
                    assertThat(migration.getVersion().getVersion())
                            .isEqualTo("1");

                    assertThat(migration.getDescription())
                            .isEqualTo("create payment events");

                    assertThat(migration.getState().isApplied())
                            .isTrue();
                });

        // Verify that the required tables exist.
        assertThat(tableExists("payment_events")).isTrue();
        assertThat(tableExists("audit_logs")).isTrue();

        // Payment events columns.
        assertColumn("payment_events", "id",
                "bigint", "NO", null, 64, 0);

        assertColumn("payment_events", "payment_id",
                "character varying", "NO", 255, null, null);

        assertColumn("payment_events", "order_id",
                "character varying", "NO", 255, null, null);

        assertColumn("payment_events", "amount",
                "numeric", "NO", null, 19, 2);

        assertColumn("payment_events", "currency",
                "character varying", "NO", 3, null, null);

        assertColumn("payment_events", "retry_count",
                "integer", "NO", null, 32, 0);

        assertColumn("payment_events", "status",
                "character varying", "NO", 50, null, null);

        assertColumn("payment_events", "failure_reason",
                "character varying", "YES", 255, null, null);

        assertColumn("payment_events", "next_retry_at",
                "timestamp without time zone", "YES", null, null, null);

        assertColumn("payment_events", "created_at",
                "timestamp without time zone", "YES", null, null, null);

        assertColumn("payment_events", "updated_at",
                "timestamp without time zone", "YES", null, null, null);

        // Audit logs columns.
        assertColumn("audit_logs", "id",
                "bigint", "NO", null, 64, 0);

        assertColumn("audit_logs", "payment_id",
                "character varying", "YES", 255, null, null);

        assertColumn("audit_logs", "action",
                "character varying", "YES", 255, null, null);

        assertColumn("audit_logs", "message",
                "character varying", "YES", 255, null, null);

        assertColumn("audit_logs", "created_at",
                "timestamp without time zone", "YES", null, null, null);

        // Verify primary keys and identity-generated IDs.
        assertPrimaryKeyOnId("payment_events");
        assertPrimaryKeyOnId("audit_logs");

        assertIdentityColumn("payment_events", "id");
        assertIdentityColumn("audit_logs", "id");

        // Verify the unique constraint and required indexes.
        assertThat(indexExists(
                "payment_events",
                "uk_payment_events_payment_id"
        )).isTrue();

        assertThat(indexExists(
                "payment_events",
                "idx_payment_events_status_next_retry_at"
        )).isTrue();

        assertThat(indexExists(
                "audit_logs",
                "idx_audit_logs_payment_id_created_at"
        )).isTrue();
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """,
                Integer.class,
                tableName
        );

        return count != null && count == 1;
    }

    private void assertColumn(
            String tableName,
            String columnName,
            String expectedDataType,
            String expectedNullable,
            Integer expectedCharacterMaximumLength,
            Integer expectedNumericPrecision,
            Integer expectedNumericScale) {

        var columns = jdbcTemplate.query(
                """
                SELECT data_type,
                       is_nullable,
                       character_maximum_length,
                       numeric_precision,
                       numeric_scale
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """,
                (rs, rowNum) -> new Object[] {
                        rs.getString("data_type"),
                        rs.getString("is_nullable"),
                        rs.getObject("character_maximum_length", Integer.class),
                        rs.getObject("numeric_precision", Integer.class),
                        rs.getObject("numeric_scale", Integer.class)
                },
                tableName,
                columnName
        );

        assertThat(columns)
                .as("Column '%s' should exist in table '%s'", columnName, tableName)
                .hasSize(1);

        Object[] column = columns.get(0);

        assertThat(column[0]).isEqualTo(expectedDataType);
        assertThat(column[1]).isEqualTo(expectedNullable);
        assertThat(column[2]).isEqualTo(expectedCharacterMaximumLength);
        assertThat(column[3]).isEqualTo(expectedNumericPrecision);
        assertThat(column[4]).isEqualTo(expectedNumericScale);
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename = ?
                  AND indexname = ?
                """,
                Integer.class,
                tableName,
                indexName
        );

        return count != null && count == 1;
    }

    private void assertPrimaryKeyOnId(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.key_column_usage kcu
                JOIN information_schema.table_constraints tc
                  ON tc.constraint_catalog = kcu.constraint_catalog
                 AND tc.constraint_schema = kcu.constraint_schema
                 AND tc.constraint_name = kcu.constraint_name
                 AND tc.table_name = kcu.table_name
                WHERE kcu.table_schema = current_schema()
                  AND kcu.table_name = ?
                  AND kcu.column_name = 'id'
                  AND tc.constraint_type = 'PRIMARY KEY'
                """,
                Integer.class,
                tableName
        );

        assertThat(count).isEqualTo(1);
    }

    private void assertIdentityColumn(String tableName, String columnName) {
        String identity = jdbcTemplate.query(
                        """
                        SELECT is_identity
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = ?
                          AND column_name = ?
                        """,
                        (rs, rowNum) -> rs.getString("is_identity"),
                        tableName,
                        columnName
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Column '" + columnName
                                + "' was not found in table '" + tableName + "'"
                ));

        assertThat(identity).isEqualTo("YES");
    }

    private static class ColumnMetadata {

        private final String dataType;
        private final String nullable;
        private final Integer characterMaximumLength;
        private final Integer numericPrecision;
        private final Integer numericScale;

        private ColumnMetadata(
                String dataType,
                String nullable,
                Integer characterMaximumLength,
                Integer numericPrecision,
                Integer numericScale) {
            this.dataType = dataType;
            this.nullable = nullable;
            this.characterMaximumLength = characterMaximumLength;
            this.numericPrecision = numericPrecision;
            this.numericScale = numericScale;
        }

        String dataType() {
            return dataType;
        }

        String nullable() {
            return nullable;
        }

        Integer characterMaximumLength() {
            return characterMaximumLength;
        }

        Integer numericPrecision() {
            return numericPrecision;
        }

        Integer numericScale() {
            return numericScale;
        }
    }
}
