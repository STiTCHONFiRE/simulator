package org.nomium.sumulator.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.nomium.sumulator.config.SimProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TelemetryService {

    SimProperties props;
    Instant startedAt = Instant.now();

    public long uptimeSeconds() {
        long s = Duration.between(startedAt, Instant.now()).getSeconds();
        return Math.max(1, s);
    }

    public String deviceId(String seed) {
        int base = Math.abs(Objects.requireNonNullElse(seed, "seed").hashCode()) % 1_000_000;
        return props.getSerialPrefix() + "-" + String.format("%06d", base);
    }

    public String macFor(String seed) {
        int h = Math.abs(Objects.requireNonNullElse(seed, "seed").hashCode());
        int b1 = (h) & 0xFF;
        int b2 = (h >> 8) & 0xFF;
        int b3 = (h >> 16) & 0xFF;
        int b4 = (h >> 24) & 0xFF;
        int b5 = (h >> 4) & 0xFF;

        return String.format(Locale.ROOT, "02:%02X:%02X:%02X:%02X:%02X", b1, b2, b3, b4, b5);
    }

    public double jitter(double base, double delta) {
        return round(base + ThreadLocalRandom.current().nextDouble(-delta, delta), 2);
    }

    private static double round(double v, int digits) {
        double p = Math.pow(10, digits);
        return Math.round(v * p) / p;
    }

    public Map<String, Object> minerConf(String clientIp) {
        List<Map<String, Object>> pools = List.of(
                Map.of("url", props.getPoolUrl(), "user", "worker1", "pass", "x"),
                Map.of("url", props.getPoolUrl(), "user", "worker2", "pass", "x"),
                Map.of("url", props.getPoolUrl(), "user", "worker3", "pass", "x")
        );

        return new LinkedHashMap<>() {{
            put("pools", pools);
            put("api-listen", true);
            put("api-network", true);
            put("vendor", props.getVendor());
            put("model", props.getModel());
            put("serial", deviceId(clientIp));
        }};
    }

    public Map<String, Object> systemInfo(String clientIp) {
        return new LinkedHashMap<>() {{
            put("minertype", props.getModel()); // важное поле для AntminerParser.ParseSystemInfo
            put("system_filesystem_version", props.getSystemFilesystemVersion()); // TryParse -> yyyyMMdd
            put("nettype", "DHCP");
            put("macaddr", macFor(clientIp));
        }};
    }

    public Map<String, Object> antminerSummaryNew(String clientIp) {
        double rate5s = jitter(props.getHashrateThs(), 4.0);
        double rateAvg = jitter(props.getHashrateThs(), 2.0);

        return new LinkedHashMap<>() {{
            put("STATUS", List.of(Map.of("STATUS", "S", "Code", 11, "Msg", "Summary")));
            put("SUMMARY", List.of(new LinkedHashMap<String, Object>() {{
                put("elapsed", uptimeSeconds());
                put("rate_5s", rate5s);
                put("rate_avg", rateAvg);
                put("rate_unit", "TH/s");
            }}));
        }};
    }

    public Map<String, Object> antminerStatusOld(String clientIp) {
        double ths5s = jitter(props.getHashrateThs(), 4.0);
        double thsAvg = jitter(props.getHashrateThs(), 2.0);

        double ghs5s = ths5s * 1000.0;   // TH/s -> GH/s
        double ghsav = thsAvg * 1000.0;  // TH/s -> GH/s

        double t1 = jitter(props.getTemperatureC(), 2.0);
        double t2 = jitter(props.getTemperatureC(), 2.0);
        int fan1 = ThreadLocalRandom.current().nextInt(4800, 6200);
        int fan2 = ThreadLocalRandom.current().nextInt(4800, 6200);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("elapsed", uptimeSeconds());
        summary.put("ghs5s", round(ghs5s, 2));
        summary.put("ghsav", round(ghsav, 2));

        Map<String, Object> dev0 = new LinkedHashMap<>();
        dev0.put("freq", "0,temp1=" + round(t1, 2) + ",temp2=" + round(t2, 2) + ",fan1=" + fan1 + ",fan2=" + fan2);
        // запасной вариант (если кто-то читает temp1/fan1 напрямую)
        dev0.put("temp1", round(t1, 2));
        dev0.put("temp2", round(t2, 2));
        dev0.put("fan1", fan1);
        dev0.put("fan2", fan2);

        return new LinkedHashMap<>() {{
            put("summary", summary);
            put("devs", List.of(dev0));
        }};
    }

    public Map<String, Object> cgminerSummary(String clientIp) {
        double temp = jitter(props.getTemperatureC(), 2.0);
        double power = jitter(props.getPowerW(), 120.0);
        double ths = jitter(props.getHashrateThs(), 4.0);

        return new LinkedHashMap<>() {{
            put("STATUS", List.of(Map.of(
                    "STATUS", "S",
                    "When", Instant.now().getEpochSecond(),
                    "Code", 11,
                    "Msg", "Summary",
                    "Description", "cgminer"
            )));
            put("SUMMARY", List.of(new LinkedHashMap<String, Object>() {{
                put("Elapsed", uptimeSeconds());
                put("MHS av", round(ths * 1_000_000.0, 3)); // TH/s -> MH/s
                put("Power", power);
                put("Temperature", temp);
                put("Found Blocks", 0);
                put("Accepted", ThreadLocalRandom.current().nextInt(10, 400));
                put("Rejected", ThreadLocalRandom.current().nextInt(0, 4));
                put("Hardware Errors", ThreadLocalRandom.current().nextInt(0, 3));
                // доп. поля на случай парсеров
                put("Type", props.getModel());
                put("Firmware", props.getFirmware());
                put("MAC", macFor(clientIp));
            }}));
            put("id", 1);
        }};
    }

    public Map<String, Object> cgminerDevDetails(String clientIp) {
        double temp = jitter(props.getTemperatureC(), 2.0);
        double power = jitter(props.getPowerW(), 120.0);
        double ths = jitter(props.getHashrateThs(), 4.0);

        Map<String, Object> dev = new LinkedHashMap<>();
        dev.put("ASC", 0);
        dev.put("Name", props.getModel());
        dev.put("Temperature", temp);
        dev.put("MHS av", round(ths * 1_000_000.0, 3));
        dev.put("Power", power);
        dev.put("Enabled", "Y");
        dev.put("Status", "Alive");
        dev.put("Type", props.getModel());

        return new LinkedHashMap<>() {{
            put("STATUS", List.of(Map.of("STATUS", "S", "Code", 9, "Msg", "Dev details", "Description", "cgminer")));
            // разные майнер-клиенты/парсеры любят разные ключи
            put("DEVDETAILS", List.of(dev));
            put("DEVS", List.of(dev));
            put("id", 1);
        }};
    }

    public Map<String, Object> cgminerStats(String clientIp) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("ID", "STATS");
        s.put("Elapsed", uptimeSeconds());
        s.put("Type", props.getModel());
        s.put("Firmware", props.getFirmware());
        s.put("MAC", macFor(clientIp));

        return new LinkedHashMap<>() {{
            put("STATUS", List.of(Map.of("STATUS", "S", "Code", 7, "Msg", "Stats", "Description", "cgminer")));
            put("STATS", List.of(s));
            put("id", 1);
        }};
    }

}
