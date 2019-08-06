package io.trofiv.revolut;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.Properties;

public class TestModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());

    @Override
    protected void configure() {
        final Properties properties = new Properties();
        try (final InputStream is = TestModule.class.getClassLoader().getResourceAsStream("application.properties")) {
            properties.load(is);
        } catch (IOException ex) {
            LOGGER.error("Can't initialize application properties", ex);
        }
        Names.bindProperties(binder(), properties);
        requestStaticInjection(DatabaseCommons.class);
    }
}
