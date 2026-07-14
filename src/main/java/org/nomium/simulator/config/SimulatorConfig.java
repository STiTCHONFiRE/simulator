package org.nomium.simulator.config;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@EnableConfigurationProperties(SimProperties.class)
@ImportRuntimeHints(SimulatorConfig.SimulatorRuntimeHints.class)
public class SimulatorConfig {

    static final class SimulatorRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerPattern("mode-dsl/*.json");
        }
    }
}
