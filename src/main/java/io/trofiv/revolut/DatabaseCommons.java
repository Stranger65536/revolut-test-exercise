package io.trofiv.revolut;

import com.google.inject.Inject;
import com.pivovarit.function.ThrowingFunction;
import io.trofiv.revolut.exception.GenericException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Common utilities for SQL operations
 */
@SuppressWarnings("UtilityClassCanBeEnum")
public final class DatabaseCommons {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());

    @Inject
    private static DatabaseService databaseService;

    private DatabaseCommons() {
    }

    /**
     * Auto closeable wrapper for simplified execution in SQL context with specified rollback and commit policies
     *
     * @param function          function to run in SQL context
     * @param rollbackBehaviour whether to do a rollback if unhandled {@link Exception} occured
     * @param commitBehavior    whether to do a commit after successful {@code function} execution
     * @param <T>               return type if a {@code function}
     * @return a value returned by {@code function}
     * @throws GenericException if any unhandled exception raised by {@code function}
     */
    public static <T> T executeWithConnection(
            final @NotNull ThrowingFunction<Connection, T, Exception> function,
            final RollbackBehaviour rollbackBehaviour,
            final CommitBehaviour commitBehavior)
            throws GenericException {
        Connection connection = null;
        //noinspection StaticVariableUsedBeforeInitialization
        try (final Connection conn = databaseService.getConnection()) {
            connection = conn;
            final T result = function.apply(conn);
            if (commitBehavior.value) {
                connection.commit();
            }
            return result;
        } catch (GenericException e) {
            // business exceptions are rethrown without wrapping
            LOGGER.error("Business error occurred", e);
            doRollbackIfNeeded(rollbackBehaviour, connection, e);
            throw e;
        } catch (Exception e) {
            LOGGER.error("Unhandled error occurred", e);
            final GenericException exception = new GenericException("Unhandled error occurred", e);
            doRollbackIfNeeded(rollbackBehaviour, connection, exception);
            throw exception;
        }
    }

    /**
     * Configures SQL connection transaction policies
     *
     * @param connection     connection to configure
     * @param isolationLevel transaction isolation level
     * @param autoCommit     whether to perform commit automatically after each executed statement
     *                       (should be disabled if you want to manage transaction manually)
     * @param isReadOnly     whether all operations executed on this connection are read only
     * @throws GenericException if any database-related error has occurred
     */
    public static void setTransactionPolicyForConnection(
            final @NotNull Connection connection,
            final int isolationLevel,
            final CommitBehaviour autoCommit,
            final boolean isReadOnly)
            throws GenericException {
        try {
            connection.setReadOnly(isReadOnly);
            connection.setAutoCommit(autoCommit.value);
            connection.setTransactionIsolation(isolationLevel);
        } catch (SQLException e) {
            LOGGER.error("Connection transaction isolation level set error", e);
            throw new GenericException("Connection transaction isolation level set error", e);
        }
    }

    private static void doRollbackIfNeeded(
            final @NotNull RollbackBehaviour rollbackBehaviour,
            final @Nullable Connection connection,
            final @NotNull GenericException exception) {
        if (rollbackBehaviour.value && connection != null) {
            try {
                LOGGER.info("Rolling back transaction");
                connection.rollback();
            } catch (SQLException ex) {
                LOGGER.warn("Rollback failed", ex);
                exception.addSuppressed(ex);
            }
        }
    }

    /**
     * Code reading helper for rollback policy
     */
    @SuppressWarnings("PublicInnerClass")
    public enum RollbackBehaviour {
        DO_ROLLBACK_ON_EXCEPTION(true),
        DO_NOT_ROLLBACK_ON_EXCEPTION(false);

        private final boolean value;

        RollbackBehaviour(final boolean value) {
            this.value = value;
        }
    }

    /**
     * Code reading helper for commit policy
     */
    @SuppressWarnings("PublicInnerClass")
    public enum CommitBehaviour {
        DO_AUTO_COMMIT(true),
        DO_NOT_AUTO_COMMIT(false);

        private final boolean value;

        CommitBehaviour(final boolean value) {
            this.value = value;
        }
    }
}
