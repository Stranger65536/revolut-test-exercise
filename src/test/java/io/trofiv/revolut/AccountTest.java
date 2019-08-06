package io.trofiv.revolut;

import io.trofiv.revolut.DatabaseCommons.CommitBehaviour;
import io.trofiv.revolut.DatabaseCommons.RollbackBehaviour;
import io.trofiv.revolut.exception.NoSuchAccountException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;

import static io.trofiv.revolut.Account.updateAccountAmountById;
import static io.trofiv.revolut.DatabaseCommons.executeWithConnection;
import static io.trofiv.revolut.DatabaseCommons.setTransactionPolicyForConnection;
import static java.sql.Connection.TRANSACTION_READ_COMMITTED;
import static org.eclipse.jetty.http.HttpStatus.Code.INTERNAL_SERVER_ERROR;
import static org.eclipse.jetty.http.HttpStatus.Code.NOT_FOUND;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestMethodOrder(OrderAnnotation.class)
class AccountTest extends AbstractIntegrationTest {

    @Test
    @Tag("EndToEnd")
    void testFetchExistingAccount() throws Exception {
        assertAccount("1", new BigDecimal("100"));
        assertAccount("2", new BigDecimal("200"));
    }

    @Test
    @Tag("EndToEnd")
    void testFetchAccountWithBrokenStoredAmount() throws Exception {
        final HttpUriRequest request = new HttpGet(getAppUrl() + "/accounts/3");
        final ResponseEntity<ResponseError> result = syncCall(request, ResponseError.class);
        assertThat(result.getStatusCode(), equalTo(INTERNAL_SERVER_ERROR.getCode()));
        final ResponseError error = result.getContent();
        assertThat(error, not(equalTo(null)));
        assertThat(error.getError(), equalTo("Table has invalid BigDecimal value for id 3: invalid_bigdecimal"));
        assertThat(error.getFrames(), not(equalTo(null)));
        assertThat(error.getFrames().isEmpty(), equalTo(false));
    }

    @Test
    @Tag("EndToEnd")
    void testFetchInExistentAccount() throws Exception {
        final HttpUriRequest request = new HttpGet(getAppUrl() + "/accounts/inexistent");
        final ResponseEntity<ResponseError> result = syncCall(request, ResponseError.class);
        assertThat(result.getStatusCode(), equalTo(NOT_FOUND.getCode()));
        final ResponseError error = result.getContent();
        assertThat(error, not(equalTo(null)));
        assertThat(error.getError(), equalTo("Account inexistent does not exist"));
        assertThat(error.getFrames(), equalTo(null));
    }

    @Test
    @Tag("Integration")
    void testUpdateExistingAccount() throws Exception {
        final BigDecimal newAmount = new BigDecimal("0");
        executeWithConnection(conn -> {
            setTransactionPolicyForConnection(conn, TRANSACTION_READ_COMMITTED,
                    CommitBehaviour.DO_NOT_AUTO_COMMIT, false);
            updateAccountAmountById(conn, "1", newAmount);
            //noinspection ReturnOfNull
            return null;
        }, RollbackBehaviour.DO_ROLLBACK_ON_EXCEPTION, CommitBehaviour.DO_AUTO_COMMIT);
        assertAccount("1", newAmount);

    }

    @Test
    @Tag("Integration")
    void testUpdateInExistentAccount() throws Exception {
        assertThrows(NoSuchAccountException.class, () ->
                executeWithConnection(conn -> {
                    setTransactionPolicyForConnection(conn, TRANSACTION_READ_COMMITTED,
                            CommitBehaviour.DO_NOT_AUTO_COMMIT, false);
                    updateAccountAmountById(conn, "inexistent", new BigDecimal("500"));
                    //noinspection ReturnOfNull
                    return null;
                }, RollbackBehaviour.DO_ROLLBACK_ON_EXCEPTION, CommitBehaviour.DO_AUTO_COMMIT));
        assertAccount("1", new BigDecimal("100"));
        assertAccount("2", new BigDecimal("200"));
    }
}
