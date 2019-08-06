package io.trofiv.revolut;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.trofiv.revolut.DatabaseCommons.CommitBehaviour;
import io.trofiv.revolut.DatabaseCommons.RollbackBehaviour;
import io.trofiv.revolut.exception.GenericException;
import io.trofiv.revolut.exception.NoSuchAccountException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static io.trofiv.revolut.DatabaseCommons.executeWithConnection;
import static io.trofiv.revolut.DatabaseCommons.setTransactionPolicyForConnection;
import static java.sql.Connection.TRANSACTION_READ_COMMITTED;

/**
 * Describes both model and operations related to accounts fetch
 */
public class Account {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());

    private static final String TABLE_NAME = "ACCOUNTS";
    private static final String ID_COLUMN = "ID";
    private static final String AMOUNT_COLUMN = "AMOUNT";
    private static final boolean READ_ONLY = true;

    private final String id;
    private final BigDecimal amount;


    @SuppressWarnings("WeakerAccess")
    public Account(final String id, final BigDecimal amount) {
        this.id = id;
        this.amount = amount;
    }

    @JsonCreator
    @SuppressWarnings("unused")
    public Account(@JsonProperty("id") final String id,
                   @JsonProperty("amount") final String amount) {
        this.id = id;
        this.amount = new BigDecimal(amount);
    }

    @JsonGetter
    public String getId() {
        return id;
    }

    @JsonGetter
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("amount", amount)
                .toString();
    }

    /**
     * Returns account by id using a new connection without a transaction. Doesn't perform commit and rollaback.
     * Uses {@link TRANSACTION_READ_COMMITTED} isolation level
     *
     * @param id specified account id to return
     * @return account for the specified id
     * @throws NoSuchAccountException if account for the specified account id doesn't exist
     * @throws GenericException       if any database-related error has occurred
     */
    @SuppressWarnings({"WeakerAccess", "JavadocReference"})
    public static @NotNull Account getAccountById(final @NotNull String id) throws GenericException {
        return executeWithConnection(conn -> {
            setTransactionPolicyForConnection(conn, TRANSACTION_READ_COMMITTED,
                    CommitBehaviour.DO_AUTO_COMMIT, READ_ONLY);
            return getAccountById(id, conn);
        }, RollbackBehaviour.DO_NOT_ROLLBACK_ON_EXCEPTION, CommitBehaviour.DO_NOT_AUTO_COMMIT);
    }

    /**
     * Updates specified account amount by id using specified sql connection
     *
     * @param conn   SQL connection to use
     * @param id     account id to update
     * @param amount amount to set
     * @throws NoSuchAccountException if account for the specified account id doesn't exist
     * @throws GenericException       if any database-related error has occurred
     */
    @SuppressWarnings("WeakerAccess")
    public static void updateAccountAmountById(
            final @NotNull Connection conn,
            final @NotNull String id,
            final @NotNull BigDecimal amount)
            throws GenericException {
        try (final PreparedStatement ps = conn.prepareStatement(
                "UPDATE " + TABLE_NAME + " SET " + AMOUNT_COLUMN + " = ? WHERE " + ID_COLUMN + "  = ?")) {
            ps.setString(1, amount.toPlainString());
            ps.setString(2, id);
            final int updated = ps.executeUpdate();
            if (updated == 0) {
                LOGGER.error("Account update updated 0 rows: id {} amount {}", id, amount);
                throw new NoSuchAccountException("Account update updated 0 rows: id " + id + " amount " + amount);
            }
        } catch (SQLException e) {
            LOGGER.error("Account prepared statement execution failed for account {} with amount {}", id, amount, e);
            throw new GenericException("Account prepared statement execution failed for account "
                    + id + " with amount " + amount, e);
        }
    }

    /**
     * Returns account by id using specified sql connection
     *
     * @param conn SQL connection to use
     * @param id   account id to update
     * @throws NoSuchAccountException if account for the specified account id doesn't exist
     * @throws GenericException       if any database-related error has occurred
     */
    @SuppressWarnings("WeakerAccess")
    public static @NotNull Account getAccountById(
            final @NotNull String id,
            final @NotNull Connection conn)
            throws GenericException {
        return getAccountById(id, conn, false);
    }

    /**
     * Returns account by id using specified sql connection
     *
     * @param conn      SQL connection to use
     * @param id        account id to update
     * @param forUpdate whether selected rows are for update
     * @throws NoSuchAccountException if account for the specified account id doesn't exist
     * @throws GenericException       if any database-related error has occurred
     */
    @SuppressWarnings("WeakerAccess")
    public static @NotNull Account getAccountById(
            final @NotNull String id,
            final @NotNull Connection conn,
            final boolean forUpdate)
            throws GenericException {
        try (final PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM " + TABLE_NAME + " WHERE " + ID_COLUMN + " = ?" + (forUpdate ? " FOR UPDATE" : ""))) {
            ps.setString(1, id);
            try (final ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return processAccountRow(id, rs);
                } else {
                    LOGGER.info("Account {} does not exist", id);
                    throw new NoSuchAccountException("Account " + id + " does not exist");
                }
            } catch (SQLException e) {
                LOGGER.error("Account query failed for account {}", id, e);
                throw new GenericException("Account query failed for account " + id, e);
            }
        } catch (SQLException e) {
            LOGGER.error("Account prepared statement execution failed for account {}", id, e);
            throw new GenericException("Account prepared statement execution failed for account " + id, e);
        }
    }

    /**
     * Processes SQL row to deserialize it to account
     *
     * @param id account id
     * @param rs sql result set
     * @return Account represented by sql result set
     * @throws SQLException     if any database-related error has occurred
     * @throws GenericException if stored amount value can't be deserialized to {@link BigDecimal}
     */
    private static @NotNull Account processAccountRow(
            final @NotNull String id,
            final @NotNull ResultSet rs)
            throws SQLException, GenericException {
        final String amount = rs.getString(AMOUNT_COLUMN);
        try {
            final Account account = new Account(id, new BigDecimal(amount));
            LOGGER.info("Account fetched: {}", account);
            return account;
        } catch (NumberFormatException e) {
            LOGGER.error("Table has invalid BigDecimal value for id {}: {}", id, amount, e);
            throw new GenericException("Table has invalid BigDecimal value for id "
                    + id + ": " + amount, e);
        }
    }
}
