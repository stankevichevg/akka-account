package com.evst.account;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.stream.Collectors;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class Boot {

    private static final String ENABLED_MODULES_SETTING_KEY = "modules.enabled";

    /**
     * Starts application according to the set up Guice module in configuration;
     *
     * Use setting `modules.enabled` to add custom modules.
     *
     * @throws InitializationException if incorrect configuration was detected
     */
    public void startApplication() throws InitializationException {
        final Config config = ConfigFactory.load("application");
        final Injector injector = Guice.createInjector(readEnabledModules(config));
        final HttpServer webServer = injector.getInstance(HttpServer.class);
        webServer.start();
    }

    private Iterable<Module> readEnabledModules(Config config) {
        return config.getStringList(ENABLED_MODULES_SETTING_KEY).stream().map(ms -> {
            try {
                Class moduleClass = getClass().getClassLoader().loadClass(ms);
                // a Module subclass with zero-args constructor is expected here
                return (Module) moduleClass.newInstance();
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | ClassCastException e) {
                throw new InitializationException("Module can't be loaded.", e);
            }
        }).collect(Collectors.toList());
    }

    /**
     * Entry point for the service. Creates {@link Boot} and give the
     *
     * @param args is not used
     * @throws InitializationException if incorrect configuration was detected
     */
    public static void main(String[] args) throws InitializationException {
        new Boot().startApplication();
    }

    /**
     * Indicates application initialization problems. Extends {@link RuntimeException} to be thrown from Java streams.
     */
    public static final class InitializationException extends RuntimeException {

        public InitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
