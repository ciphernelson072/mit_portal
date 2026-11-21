package com.sis.config;

import org.hibernate.dialect.MySQLDialect;

public class TiDbMySqlDialect extends MySQLDialect {
    @Override
    protected boolean supportsAliasLocks() {
        return false;
    }
}
