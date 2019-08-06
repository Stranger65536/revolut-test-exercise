package io.trofiv.revolut;

import org.apache.http.HttpResponse;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.eclipse.jetty.http.HttpStatus.Code.BAD_REQUEST;
import static org.eclipse.jetty.http.HttpStatus.Code.FORBIDDEN;
import static org.eclipse.jetty.http.HttpStatus.Code.INTERNAL_SERVER_ERROR;
import static org.eclipse.jetty.http.HttpStatus.Code.NO_CONTENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.fail;

@TestMethodOrder(OrderAnnotation.class)
class TransferTest extends AbstractIntegrationTest {

    @Test
    @Tag("EndToEnd")
    void testValidTransfer() throws Exception {
        final ResponseEntity<ResponseError> result = makeTransfer("2", "1", "50.01");
        assertThat(result.getStatusCode(), equalTo(NO_CONTENT.getCode()));
        assertAccount("1", new BigDecimal("150.01"));
        assertAccount("2", new BigDecimal("149.99"));
    }

    @Test
    @Tag("EndToEnd")
    void testTransferSameAccount() throws Exception {
        final ResponseEntity<ResponseError> result = makeTransfer("1", "1", "10");
        assertThat(result.getStatusCode(), equalTo(BAD_REQUEST.getCode()));
        final ResponseError error = result.getContent();
        assertThat(error, not(CoreMatchers.equalTo(null)));
        assertThat(error.getError(), CoreMatchers.equalTo("Source and target accounts can't be the same!"));
        assertThat(error.getFrames(), not(CoreMatchers.equalTo(null)));
        assertThat(error.getFrames().isEmpty(), CoreMatchers.equalTo(false));
        assertAccount("1", new BigDecimal("100"));
        assertAccount("2", new BigDecimal("200"));
    }

    @Test
    @Tag("EndToEnd")
    void testTransferZeroAmount() throws Exception {
        final ResponseEntity<ResponseError> result = makeTransfer("1", "2", "0");
        assertThat(result.getStatusCode(), equalTo(BAD_REQUEST.getCode()));
        final ResponseError error = result.getContent();
        assertThat(error, not(CoreMatchers.equalTo(null)));
        assertThat(error.getError(), CoreMatchers.equalTo("Amount must be positive! Got: 0"));
        assertThat(error.getFrames(), not(CoreMatchers.equalTo(null)));
        assertThat(error.getFrames().isEmpty(), CoreMatchers.equalTo(false));
        assertAccount("1", new BigDecimal("100"));
        assertAccount("2", new BigDecimal("200"));
    }

    @Test
    @Tag("EndToEnd")
    void testTransferNegativeAmount() throws Exception {
        final ResponseEntity<ResponseError> result = makeTransfer("1", "2", "-1");
        assertThat(result.getStatusCode(), equalTo(BAD_REQUEST.getCode()));
        final ResponseError error = result.getContent();
        assertThat(error, not(CoreMatchers.equalTo(null)));
        assertThat(error.getError(), CoreMatchers.equalTo("Amount must be positive! Got: -1"));
        assertThat(error.getFrames(), not(CoreMatchers.equalTo(null)));
        assertThat(error.getFrames().isEmpty(), CoreMatchers.equalTo(false));
        assertAccount("1", new BigDecimal("100"));
        assertAccount("2", new BigDecimal("200"));
    }

    @Test
    @Tag("EndToEnd")
    void testTransferInvalidAmount() throws Exception {
        final ResponseEntity<ResponseError> result = makeTransfer("1", "2", "invalid");
        assertThat(result.getStatusCode(), equalTo(BAD_REQUEST.getCode()));
        final ResponseError error = result.getContent();
        assertThat(error, not(CoreMatchers.equalTo(null)));
        assertThat(error.getError(), CoreMatchers.equalTo("Invalid request!"));
        assertThat(error.getFrames(), not(CoreMatchers.equalTo(null)));
        assertThat(error.getFrames().isEmpty(), CoreMatchers.equalTo(false));
        assertAccount("1", new BigDecimal("100"));
        assertAccount("2", new BigDecimal("200"));
    }

    @Test
    @Tag("EndToEnd")
    void testTransferInvalidStoredAmount() throws Exception {
        final ResponseEntity<ResponseError> result = makeTransfer("1", "3", "10");
        assertThat(result.getStatusCode(), CoreMatchers.equalTo(INTERNAL_SERVER_ERROR.getCode()));
        final ResponseError error = result.getContent();
        assertThat(error, not(CoreMatchers.equalTo(null)));
        assertThat(error.getError(), CoreMatchers.equalTo(
                "Table has invalid BigDecimal value for id 3: invalid_bigdecimal"));
        assertThat(error.getFrames(), not(CoreMatchers.equalTo(null)));
        assertThat(error.getFrames().isEmpty(), CoreMatchers.equalTo(false));
        assertAccount("1", new BigDecimal("100"));
    }

    @Test
    @Tag("EndToEnd")
    void testTransferInsufficientFunds() throws Exception {
        final ResponseEntity<ResponseError> result = makeTransfer("1", "2", "10000");
        assertThat(result.getStatusCode(), equalTo(FORBIDDEN.getCode()));
        final ResponseError error = result.getContent();
        assertThat(error, not(CoreMatchers.equalTo(null)));
        assertThat(error.getError(), CoreMatchers.equalTo(
                "Credited account has insufficient funds for credit 10000: 100.0"));
        assertThat(error.getFrames(), CoreMatchers.equalTo(null));
        assertAccount("1", new BigDecimal("100"));
        assertAccount("2", new BigDecimal("200"));
    }

    @Test
    @Tag("ConcurrentTest")
    void testConcurrentTransfersBetweenTwoAccounts() throws IOException {
        final int requests = 1000;
        final Collection<Future<HttpResponse>> futures = new ArrayList<>(requests);
        for (int i = 0; i < requests; i++) {
            futures.add(makeAsyncTransfer("1", "2", "0.01"));
            futures.add(makeAsyncTransfer("2", "1", "0.02"));
        }
        for (Future<HttpResponse> future : futures) {
            try {
                final HttpResponse response = future.get();
                final ResponseEntity<ResponseError> result = parseResponse(ResponseError.class, response);
                assertThat(result.getStatusCode(), equalTo(NO_CONTENT.getCode()));
            } catch (InterruptedException | ExecutionException e) {
                fail("Transfer request failed!", e);
            }
        }
        assertAccount("1", new BigDecimal("110"));
        assertAccount("2", new BigDecimal("190"));
    }
}
