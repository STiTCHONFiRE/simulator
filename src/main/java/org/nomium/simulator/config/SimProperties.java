package org.nomium.simulator.config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "sim")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SimProperties {

    String vendor = "Bitmain";
    String model = "Antminer S19j Pro";
    String firmware = "2025.11";
    String systemFilesystemVersion = "2025-11-01";
    String subtype = "AMLCtrl_BHB42XXX";
    String serialPrefix = "SIM";
    String identitySeed = "simulator";
    String defaultWorkMode = "normal";
    String modeOptions = "0:Normal,1:Sleep,3:High";

    double powerW = 3050;
    double hashrateThs = 104;
    double hashrateKsol = 840;
    double temperatureC = 67;
    double telemetryJitterPercent = 2.0;
    double idleTemperatureC = 30.0;
    double idleTemperatureDeltaC = 15.0;

    Duration rebootDowntime = Duration.ofSeconds(15);

    String poolUrl = "stratum+tcp://pool.example.com:3333";

    final Auth auth = new Auth();
    final Cgminer cgminer = new Cgminer();
    final ModeDsl modeDsl = new ModeDsl();

    @Data
    public static final class Auth {
        private String username;
        private String password;
    }

    @Data
    public static final class Cgminer {
        private String portsCsv = "4028,4029";
        private int socketReadTimeoutMs = 1000;
    }

    @Data
    public static final class ModeDsl {
        private String rulesPath = "classpath:mode-dsl";
        private String ruleKey = "";
    }

}
