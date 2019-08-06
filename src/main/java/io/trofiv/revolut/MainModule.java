package io.trofiv.revolut;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Properties;

/**
 * Entry point class
 */
public class MainModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());

    @Override
    protected void configure() {
        final Properties properties = new Properties();
        try (final FileInputStream fis = new FileInputStream("application.properties")) {
            properties.load(fis);
        } catch (IOException ex) {
            LOGGER.error("Can't initialize application properties", ex);
        }
        Names.bindProperties(binder(), properties);
        requestStaticInjection(DatabaseCommons.class);
    }
}
