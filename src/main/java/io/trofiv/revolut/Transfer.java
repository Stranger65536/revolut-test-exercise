package io.trofiv.revolut;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.trofiv.revolut.DatabaseCommons.CommitBehaviour;
import io.trofiv.revolut.DatabaseCommons.RollbackBehaviour;
import io.trofiv.revolut.exception.GenericException;
import io.trofiv.revolut.exception.InvalidRequestException;
import io.trofiv.revolut.exception.NoSuchAccountException;
import io.trofiv.revolut.exception.NotEnoughMoneyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;

import static io.trofiv.revolut.Account.getAccountById;
import static io.trofiv.revolut.Account.updateAccountAmountById;
import static io.trofiv.revolut.DatabaseCommons.executeWithConnection;
import static io.trofiv.revolut.DatabaseCommons.setTransactionPolicyForConnection;
import static java.sql.Connection.TRANSACTION_REPEATABLE_READ;

/**
 * Describes both model and operations related to transfers between accounts
 */
public class Transfer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());
    private static final boolean NO_AUTO_COMMIT = false;
    private static final boolean FOR_UPDATE = true;

    private final String sourceAccountId;
    private final String targetAccountId;
    private final BigDecimal amount;

    @JsonCreator
    public Transfer(
            @JsonProperty("sourceAccountId") final String sourceAccountId,
            @JsonProperty("targetAccountId") final String targetAccountId,
            @JsonProperty("amount") final String amount) {
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = new BigDecimal(amount);
    }

    /**
     * Performs a transfer between specified accounts for the specified amount
     *
     * @throws InvalidRequestException if specified amount is not positive or source and target accounts are the same
     * @throws NotEnoughMoneyException if credited account has insufficient funds
     * @throws NoSuchAccountException  if either credited or debited account dosen't exist
     * @throws GenericException        if any database error has occurred
     */
    @SuppressWarnings("WeakerAccess")
    public void makeTransfer() throws GenericException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            LOGGER.error("Transfer from '{}' to '{}': {}", sourceAccountId, targetAccountId, amount);
            throw new InvalidRequestException("Amount must be positive! Got: " + amount);
        }
        if (sourceAccountId.equals(targetAccountId)) {
            LOGGER.error("Source and target accounts can't be the same: {}", sourceAccountId);
            throw new InvalidRequestException("Source and target accounts can't be the same!");
        }
        final boolean sourceAccountLockFirst = sourceAccountId.compareTo(targetAccountId) < 0;
        executeWithConnection(conn -> {
            setTransactionPolicyForConnection(conn, TRANSACTION_REPEATABLE_READ,
                    CommitBehaviour.DO_NOT_AUTO_COMMIT, NO_AUTO_COMMIT);
            final Account sourceAccount;
            final Account targetAccount;
            // account query ordering is needed in order do don't get deadlock
            if (sourceAccountLockFirst) {
                sourceAccount = getAccountById(sourceAccountId, conn, FOR_UPDATE);
                targetAccount = getAccountById(targetAccountId, conn, FOR_UPDATE);
            } else {
                targetAccount = getAccountById(targetAccountId, conn, FOR_UPDATE);
                sourceAccount = getAccountById(sourceAccountId, conn, FOR_UPDATE);
            }
            final BigDecimal creditedSourceAccountAmount = sourceAccount.getAmount().subtract(amount);
            if (creditedSourceAccountAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new NotEnoughMoneyException("Credited account has insufficient funds for credit "
                        + amount + ": " + sourceAccount.getAmount());
            }
            final BigDecimal debitedTargetAccountAmount = targetAccount.getAmount().add(amount);
            updateAccountAmountById(conn, sourceAccountId, creditedSourceAccountAmount);
            updateAccountAmountById(conn, targetAccountId, debitedTargetAccountAmount);
            LOGGER.info("Transfer {}", this);
            //noinspection ReturnOfNull
            return null;
        }, RollbackBehaviour.DO_ROLLBACK_ON_EXCEPTION, CommitBehaviour.DO_AUTO_COMMIT);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("sourceAccountId", sourceAccountId)
                .add("targetAccountId", targetAccountId)
                .add("amount", amount)
                .toString();
    }
}
