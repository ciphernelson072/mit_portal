package com.sis.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TiDbMySqlDialectTest {
    @Test
    void disablesAliasSpecificRowLocks() {
        TiDbMySqlDialect dialect = new TiDbMySqlDialect();

        assertThat(dialect.getForUpdateString("student_alias"))
                .isEqualTo(dialect.getForUpdateString());
    }
}
