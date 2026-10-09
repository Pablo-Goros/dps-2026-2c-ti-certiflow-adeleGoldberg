package ar.edu.itba.dps.certification.infrastructure.persistence;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.util.UUID;

/** A fresh, isolated H2 database with the real migrations applied. */
public final class TestDatabase {

    private TestDatabase() {
    }

    /** A migrated database; open it with {@link JdbcPersistence#open(DataSource)} as many times as needed. */
    public static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        JdbcPersistence.migrate(dataSource);
        return dataSource;
    }

    public static JdbcPersistence create() {
        return JdbcPersistence.open(dataSource());
    }
}
