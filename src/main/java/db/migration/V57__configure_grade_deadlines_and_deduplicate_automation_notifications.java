package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class V57__configure_grade_deadlines_and_deduplicate_automation_notifications extends BaseJavaMigration {

    @Override
    public boolean canExecuteInTransaction() {
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        ensureColumn(connection, "academic_terms", "grade_submission_deadline",
                "ALTER TABLE academic_terms ADD COLUMN grade_submission_deadline DATE NULL");
        ensureColumn(connection, "notifications", "automation_key",
                "ALTER TABLE notifications ADD COLUMN automation_key VARCHAR(255) NULL");
        ensureUniqueIndex(connection, "notifications", "automation_key",
                "CREATE UNIQUE INDEX uk_notification_automation_key ON notifications (automation_key)");
    }

    private static void ensureColumn(Connection connection, String table, String column, String ddl)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            if (columns.next()) {
                return;
            }
        }
        execute(connection, ddl);
    }

    private static void ensureUniqueIndex(Connection connection, String table, String column, String ddl)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        Map<String, Integer> indexWidths = new HashMap<>();
        Set<String> indexesOnColumn = new HashSet<>();
        try (ResultSet indexes = metadata.getIndexInfo(connection.getCatalog(), null, table, true, false)) {
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
        if (indexesOnColumn.stream().anyMatch(indexName -> indexWidths.get(indexName) == 1)) {
            return;
        }
        execute(connection, ddl);
    }

    private static void execute(Connection connection, String ddl) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }
}
