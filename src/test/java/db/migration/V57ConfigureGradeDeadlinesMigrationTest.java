package db.migration;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V57ConfigureGradeDeadlinesMigrationTest {

    @Test
    void resumesFromPartiallyAppliedDdlAndCanRunAgain() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:v57_partial;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE academic_terms (id BIGINT PRIMARY KEY, grade_submission_deadline DATE NULL)");
            statement.execute("CREATE TABLE notifications (id BIGINT PRIMARY KEY)");

            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            V57__configure_grade_deadlines_and_deduplicate_automation_notifications migration =
                    new V57__configure_grade_deadlines_and_deduplicate_automation_notifications();

            migration.migrate(context);
            migration.migrate(context);

            assertColumnExists(connection, "academic_terms", "grade_submission_deadline");
            assertColumnExists(connection, "notifications", "automation_key");
            assertTrue(hasSingleColumnUniqueIndex(connection, "notifications", "automation_key"));
        }
    }

    @Test
    void addsSingleColumnConstraintWhenOnlyACompositeUniqueIndexExists() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:v57_composite;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE academic_terms (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE notifications (id BIGINT PRIMARY KEY, "
                    + "automation_key VARCHAR(255) NULL, channel VARCHAR(40) NOT NULL, "
                    + "CONSTRAINT uq_notification_key_channel UNIQUE (automation_key, channel))");

            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V57__configure_grade_deadlines_and_deduplicate_automation_notifications().migrate(context);

            assertTrue(hasSingleColumnUniqueIndex(connection, "notifications", "automation_key"));
        }
    }

    private static void assertColumnExists(Connection connection, String table, String column) throws Exception {
        try (ResultSet columns = connection.getMetaData()
                .getColumns(connection.getCatalog(), null, table, column)) {
            assertTrue(columns.next(), table + "." + column + " should exist");
        }
    }

    private static boolean hasSingleColumnUniqueIndex(Connection connection, String table, String column)
            throws Exception {
        Map<String, Integer> indexWidths = new HashMap<>();
        Set<String> indexesOnColumn = new HashSet<>();
        try (ResultSet indexes = connection.getMetaData()
                .getIndexInfo(connection.getCatalog(), null, table, true, false)) {
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                if (indexName == null) {
                    continue;
                }
                indexWidths.merge(indexName, 1, Integer::sum);
                if (column.equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) {
                    indexesOnColumn.add(indexName);
                }
            }
        }
        return indexesOnColumn.stream().anyMatch(indexName -> indexWidths.get(indexName) == 1);
    }
}
