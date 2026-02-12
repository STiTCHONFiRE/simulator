package org.nomium.sumulator.config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sim")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SimProperties {

    String vendor = "Bitmain";
    String model = "Antminer S19j Pro";
    String firmware = "2025.11";
    String systemFilesystemVersion = "2025-11-01";
    String serialPrefix = "SIM";

    double powerW = 3050;
    double hashrateThs = 104;
    double temperatureC = 67;

    String poolUrl = "stratum+tcp://pool.example.com:3333";

    final Auth auth = new Auth();
    final Cgminer cgminer = new Cgminer();

    @Data
    public static final class Auth {
        private String username = "root";
        private String password = "root";
    }

    @Data
    public static final class Cgminer {
        private String portsCsv = "4028,4029";
        private int socketReadTimeoutMs = 1000;
    }

}
