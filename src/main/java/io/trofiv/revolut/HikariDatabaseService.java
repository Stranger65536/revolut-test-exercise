package io.trofiv.revolut;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * HikariCP SQL connections pool
 */
@Singleton
public class HikariDatabaseService implements DatabaseService {
    private final HikariDataSource dataSource;

    @Inject
    public HikariDatabaseService(
            @Named("db.classname") final String dbClassName,
            @Named("db.connectionTestString") final String connectionTestString,
            @Named("db.url") final String dbUrl,
            @Named("db.user") final String dbUser,
            @Named("db.password") final String dbPassword) {
        final HikariConfig config = new HikariConfig();
        config.setDataSourceClassName(dbClassName);
        config.setConnectionTestQuery(connectionTestString);
        config.addDataSourceProperty("URL", dbUrl);
        config.addDataSourceProperty("user", dbUser);
        config.addDataSourceProperty("password", dbPassword);
        dataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
