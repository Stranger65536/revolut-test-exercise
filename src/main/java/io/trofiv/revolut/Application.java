package io.trofiv.revolut;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.javalin.Javalin;
import io.trofiv.revolut.exception.InvalidRequestException;
import io.trofiv.revolut.exception.NoSuchAccountException;
import io.trofiv.revolut.exception.NotEnoughMoneyException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import static org.eclipse.jetty.http.HttpStatus.Code.BAD_REQUEST;
import static org.eclipse.jetty.http.HttpStatus.Code.FORBIDDEN;
import static org.eclipse.jetty.http.HttpStatus.Code.INTERNAL_SERVER_ERROR;
import static org.eclipse.jetty.http.HttpStatus.Code.NOT_FOUND;
import static org.eclipse.jetty.http.HttpStatus.Code.NO_CONTENT;
import static org.eclipse.jetty.http.HttpStatus.Code.OK;

@Singleton
/**
 * Main application class. Starts lightweight jetty embedded server for serving requests
 */
public class Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());
    private static final String DEFAULT_EXC_SERIALIZATION =
            "{\"error\": \"Exception serialization failed, please check logs for details\"}";
    private static final String APPLICATION_JSON = "application/json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final int port;
    private Javalin app;

    @Inject
    public Application(@Named("server.port") final int port) {
        this.port = port;
    }

    /**
     * Starts application server and configured endpoints mapping
     */
    @SuppressWarnings("OverlyLongMethod")
    public void start() {
        app = Javalin.create().start(port);

        app.get("/health", ctx -> {
            ctx.status(OK.getCode());
            ctx.contentType(APPLICATION_JSON);
            ctx.result(JSON.writeValueAsString(ImmutableMap.of("status", "ok")));
        });

        app.get("/accounts/:id", ctx -> {
            ctx.contentType(APPLICATION_JSON);
            final Account account = Account.getAccountById(ctx.pathParam("id"));
            ctx.status(OK.getCode());
            ctx.contentType(APPLICATION_JSON);
            ctx.result(JSON.writeValueAsString(account));
        }).exception(NoSuchAccountException.class, (e, ctx) -> {
            ctx.status(NOT_FOUND.getCode());
            ctx.contentType(APPLICATION_JSON);
            ctx.result(getExceptionInfo(e, true));
        }).exception(Exception.class, (e, ctx) -> {
            ctx.status(INTERNAL_SERVER_ERROR.getCode());
            ctx.contentType(APPLICATION_JSON);
            ctx.result(getExceptionInfo(e, false));
        });

        app.post("/transfers", ctx -> {
            final String body = ctx.body();
            final Transfer transfer;
            try {
                transfer = JSON.readValue(body, Transfer.class);
            } catch (IOException e) {
                throw new InvalidRequestException("Invalid request!", e);
            }
            transfer.makeTransfer();
            ctx.status(NO_CONTENT.getCode());
        }).exception(NoSuchAccountException.class, (e, ctx) -> {
            ctx.status(NOT_FOUND.getCode());
            ctx.contentType(APPLICATION_JSON);
            ctx.result(getExceptionInfo(e, true));
        }).exception(InvalidRequestException.class, (e, ctx) -> {
            ctx.status(BAD_REQUEST.getCode());
            ctx.contentType(APPLICATION_JSON);
            ctx.result(getExceptionInfo(e, false));
        }).exception(NotEnoughMoneyException.class, (e, ctx) -> {
            ctx.status(FORBIDDEN.getCode());
            ctx.contentType(APPLICATION_JSON);
            ctx.result(getExceptionInfo(e, true));
        }).exception(Exception.class, (e, ctx) -> {
            ctx.status(INTERNAL_SERVER_ERROR.getCode());
            ctx.contentType(APPLICATION_JSON);
            ctx.result(getExceptionInfo(e, false));
        });
    }

    /**
     * Stops application server
     */
    public void stop() {
        app.stop();
    }

    public int getPort() {
        return port;
    }

    public static void main(final String[] args) {
        final Injector injector = Guice.createInjector(new MainModule());
        final Application application = injector.getInstance(Application.class);
        application.start();
    }

    /**
     * Performs exception jsonification
     *
     * @param e          exception to jsonify
     * @param omitFrames whether include full stack frames or just message
     * @return String with json representation of the specified exception. If exception failed to serialize,
     * returns {@link Application.DEFAULT_EXC_SERIALIZATION}
     */
    @SuppressWarnings("JavadocReference")
    private static String getExceptionInfo(final Exception e, final boolean omitFrames) {
        try {
            final Object value = omitFrames
                    ? ImmutableMap.of("error", e.getMessage())
                    : ImmutableMap.of("error", e.getMessage(), "frames", ExceptionUtils.getStackFrames(e));
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            LOGGER.error("Can't serialize exception trace", ex);
            return DEFAULT_EXC_SERIALIZATION;
        }
    }
}
