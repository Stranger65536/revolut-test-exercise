package io.trofiv.revolut;

import com.google.inject.ImplementedBy;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface for SQL connections retrieval. Default implementation is HikariCP
 */
@SuppressWarnings({"ClassReferencesSubclass", "InterfaceMayBeAnnotatedFunctional"})
@ImplementedBy(HikariDatabaseService.class)
public interface DatabaseService {
    Connection getConnection() throws SQLException;
}
