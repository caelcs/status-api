package dev.status;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Single shared Postgres container for the whole suite, started once in a
 * static initializer so it survives across test classes (never stopped until
 * JVM exit).
 */
public final class PostgresHolder {

    public static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    static {
        postgres.start();
    }

    private PostgresHolder() {
    }
}
