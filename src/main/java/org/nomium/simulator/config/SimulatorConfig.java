package org.nomium.simulator.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SimProperties.class)
public class SimulatorConfig {
}
