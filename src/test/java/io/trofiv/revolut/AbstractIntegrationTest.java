package io.trofiv.revolut;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.trofiv.revolut.DatabaseCommons.CommitBehaviour;
import io.trofiv.revolut.DatabaseCommons.RollbackBehaviour;
import io.trofiv.revolut.exception.GenericException;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.awaitility.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static io.trofiv.revolut.DatabaseCommons.setTransactionPolicyForConnection;
import static java.sql.Connection.TRANSACTION_READ_COMMITTED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.eclipse.jetty.http.HttpStatus.Code.OK;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.not;

@SuppressWarnings("AbstractClassWithoutAbstractMethods")
abstract class AbstractIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final CloseableHttpClient HTTP_CLIENT = HttpClientBuilder.create()
            .disableAutomaticRetries().setMaxConnTotal(100).build();
    private static final CloseableHttpAsyncClient HTTP_ASYNC_CLIENT = HttpAsyncClientBuilder.create()
            .setMaxConnTotal(5000).build();

    private static Application application;
    private static String applicationUrl;

    @BeforeEach
    @SuppressWarnings("SqlNoDataSourceInspection")
    void setUpTest() throws GenericException {
        LOGGER.info("Resetting database with default test values");
        DatabaseCommons.executeWithConnection(conn -> {
            setTransactionPolicyForConnection(conn, TRANSACTION_READ_COMMITTED,
                    CommitBehaviour.DO_AUTO_COMMIT, false);
            try (final CallableStatement statement = conn.prepareCall("DROP TABLE IF EXISTS ACCOUNTS; " +
                    "CREATE TABLE ACCOUNTS(ID VARCHAR(255) NOT NULL PRIMARY KEY, AMOUNT VARCHAR(255)); " +
                    "INSERT INTO ACCOUNTS SELECT * FROM (" +
                    "SELECT '1', '100.0' UNION " +
                    "SELECT '2', '200.0' UNION " +
                    "SELECT '3', 'invalid_bigdecimal') X;")) {
                statement.execute();
            }
            //noinspection ReturnOfNull
            return null;
        }, RollbackBehaviour.DO_NOT_ROLLBACK_ON_EXCEPTION, CommitBehaviour.DO_NOT_AUTO_COMMIT);
    }

    @BeforeAll
    static void setUp() {
        final Injector injector = Guice.createInjector(new TestModule());
        application = injector.getInstance(Application.class);
        applicationUrl = "http://localhost:" + application.getPort();
        application.start();
        await().atMost(5, SECONDS).pollDelay(Duration.ONE_SECOND).until(() -> {
            LOGGER.info("Waiting server to start");
            try {
                final HttpUriRequest request = new HttpGet(applicationUrl + "/health");
                final ResponseEntity<Map> health = syncCall(request, Map.class);
                if (health.getStatusCode() != OK.getCode()) {
                    return false;
                }
                return health.getContent() != null && "ok".equals(health.getContent().get("status"));
            } catch (Exception e) {
                return false;
            }
        });
        HTTP_ASYNC_CLIENT.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        //noinspection StaticVariableUsedBeforeInitialization
        application.stop();
        HTTP_ASYNC_CLIENT.close();
    }

    @SuppressWarnings("SuspiciousGetterSetter")
    static String getAppUrl() {
        //noinspection StaticVariableUsedBeforeInitialization
        return applicationUrl;
    }

    static <T> ResponseEntity<T> syncCall(
            final HttpUriRequest request,
            final Class<T> clazz)
            throws IOException {
        try (final CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
            return parseResponse(clazz, response);
        }
    }

    static @NotNull <T> ResponseEntity<T> parseResponse(
            final Class<T> clazz, final HttpResponse response) throws IOException {
        final HttpEntity entity = response.getEntity();
        final int code = response.getStatusLine().getStatusCode();
        final @Nullable T result;
        if (entity == null) {
            result = null;
        } else {
            final String content = IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8);
            result = JSON.readValue(content, clazz);
        }

        return new ResponseEntity<>(code, result);
    }

    @SuppressWarnings("WeakerAccess")
    static Future<HttpResponse> asyncCall(final HttpUriRequest request) {
        return HTTP_ASYNC_CLIENT.execute(request, null);
    }

    static ResponseEntity<ResponseError> makeTransfer(
            final String sourceId,
            final String targetId,
            final String amount) throws IOException {
        final String body = JSON.writeValueAsString(ImmutableMap.of(
                "sourceAccountId", sourceId,
                "targetAccountId", targetId,
                "amount", amount));
        //noinspection StaticVariableUsedBeforeInitialization
        final HttpPost request = new HttpPost(applicationUrl + "/transfers");
        final HttpEntity requestEntity = new StringEntity(body, ContentType.APPLICATION_JSON);
        request.setEntity(requestEntity);
        return syncCall(request, ResponseError.class);
    }

    static Future<HttpResponse> makeAsyncTransfer(
            final String sourceId,
            final String targetId,
            final String amount) throws IOException {
        final String body = JSON.writeValueAsString(ImmutableMap.of(
                "sourceAccountId", sourceId,
                "targetAccountId", targetId,
                "amount", amount));
        //noinspection StaticVariableUsedBeforeInitialization
        final HttpPost request = new HttpPost(applicationUrl + "/transfers");
        final HttpEntity requestEntity = new StringEntity(body, ContentType.APPLICATION_JSON);
        request.setEntity(requestEntity);
        return asyncCall(request);
    }

    static void assertAccount(
            final String id,
            final BigDecimal amount)
            throws IOException {
        //noinspection StaticVariableUsedBeforeInitialization
        final HttpUriRequest request = new HttpGet(applicationUrl + "/accounts/" + id);
        final ResponseEntity<Account> result = syncCall(request, Account.class);
        assertThat(result.getStatusCode(), equalTo(OK.getCode()));
        final Account account = result.getContent();
        assertThat(account, not(equalTo(null)));
        assertThat(account.getId(), equalTo(id));
        assertThat(account.getAmount(), comparesEqualTo(amount));
    }

    static class ResponseEntity<T> {
        private final int statusCode;
        private final T content;

        ResponseEntity(final int statusCode, final @Nullable T content) {
            this.statusCode = statusCode;
            this.content = content;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("statusCode", statusCode)
                    .add("content", content)
                    .toString();
        }

        int getStatusCode() {
            return statusCode;
        }

        @Nullable T getContent() {
            return content;
        }
    }

    static class ResponseError {
        private final String error;
        private final List<String> frames;

        @JsonCreator
        ResponseError(@JsonProperty("error") final String error,
                      @JsonProperty("frames") final List<String> frames) {
            this.error = error;
            this.frames = frames;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("error", error)
                    .add("frames", frames)
                    .toString();
        }

        String getError() {
            return error;
        }

        List<String> getFrames() {
            return frames;
        }
    }
}
