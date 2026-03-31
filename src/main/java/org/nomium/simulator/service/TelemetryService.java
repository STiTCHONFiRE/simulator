package org.nomium.simulator.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.nomium.simulator.config.SimProperties;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TelemetryService {

    private static final HexFormat MAC_FORMAT = HexFormat.ofDelimiter(":").withUpperCase();

    SimProperties props;
    AntminerStateService antState;

    @NonFinal
    volatile Instant startedAt = Instant.now();

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt == null ? Instant.now() : startedAt;
    }

    public long uptimeSeconds() {
        long s = Duration.between(startedAt, Instant.now()).getSeconds();
        return Math.max(1, s);
    }

    public String deviceId() {
        byte[] digest = sha256("device|" + identitySeed());
        long value = Integer.toUnsignedLong(ByteBuffer.wrap(digest, 0, Integer.BYTES).getInt());
        int base = (int) (value % 1_000_000);
        return props.getSerialPrefix() + "-" + String.format("%06d", base);
    }

    public String macFor() {
        byte[] digest = sha256("mac|" + identitySeed());
        byte[] mac = Arrays.copyOf(digest, 6);

        mac[0] = (byte) ((mac[0] | 0x02) & 0xFE);

        return MAC_FORMAT.formatHex(mac);
    }

    public double jitter(double base, double delta) {
        return round(base + ThreadLocalRandom.current().nextDouble(-delta, delta), 2);
    }

    private static double round(double v, int digits) {
        double p = Math.pow(10, digits);
        return Math.round(v * p) / p;
    }

    private String identitySeed() {
        String seed = props.getIdentitySeed();
        if (seed == null || seed.isBlank()) {
            return props.getSerialPrefix() + "|" + props.getVendor() + "|" + props.getModel();
        }
        return seed;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public Map<String, Object> minerConf() {
        var snap = antState.snapshot();

        List<Map<String, Object>> pools = List.of(
                Map.of("url", snap.p1().getUrl(), "user", snap.p1().getUser(), "pass", snap.p1().getPw()),
                Map.of("url", snap.p2().getUrl(), "user", snap.p2().getUser(), "pass", snap.p2().getPw()),
                Map.of("url", snap.p3().getUrl(), "user", snap.p3().getUser(), "pass", snap.p3().getPw())
        );

        return new LinkedHashMap<>() {{
            put("pools", pools);
            put("api-listen", true);
            put("api-network", true);
            put("vendor", props.getVendor());
            put("model", props.getModel());
            put("serial", deviceId());

            put("_ant_pool1url", snap.p1().getUrl());
            put("_ant_pool1user", snap.p1().getUser());
            put("_ant_pool1pw", snap.p1().getPw());

            put("_ant_pool2url", snap.p2().getUrl());
            put("_ant_pool2user", snap.p2().getUser());
            put("_ant_pool2pw", snap.p2().getPw());

            put("_ant_pool3url", snap.p3().getUrl());
            put("_ant_pool3user", snap.p3().getUser());
            put("_ant_pool3pw", snap.p3().getPw());

            put("_ant_work_mode", snap.workMode());
            put("bitmain-work-mode", snap.workMode());
        }};
    }

    public Map<String, Object> systemInfo() {
        return new LinkedHashMap<>() {{
            put("minertype", props.getModel());
            put("system_filesystem_version", props.getSystemFilesystemVersion());
            put("nettype", "DHCP");
            put("macaddr", macFor());
        }};
    }

    public Map<String, Object> antminerSummaryNew() {
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

    public Map<String, Object> antminerStatusOld() {
        double ths5s = jitter(props.getHashrateThs(), 4.0);
        double thsAvg = jitter(props.getHashrateThs(), 2.0);

        double ghs5s = ths5s * 1000.0;
        double ghsav = thsAvg * 1000.0;

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
        dev0.put("temp1", round(t1, 2));
        dev0.put("temp2", round(t2, 2));
        dev0.put("fan1", fan1);
        dev0.put("fan2", fan2);

        return new LinkedHashMap<>() {{
            put("summary", summary);
            put("devs", List.of(dev0));
        }};
    }

    public Map<String, Object> cgminerSummary() {
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
                put("MHS av", round(ths * 1_000_000.0, 3));
                put("Power", power);
                put("Temperature", temp);
                put("Found Blocks", 0);
                put("Accepted", ThreadLocalRandom.current().nextInt(10, 400));
                put("Rejected", ThreadLocalRandom.current().nextInt(0, 4));
                put("Hardware Errors", ThreadLocalRandom.current().nextInt(0, 3));
                put("Type", props.getModel());
                put("Firmware", props.getFirmware());
                put("MAC", macFor());
            }}));
            put("id", 1);
        }};
    }

    public Map<String, Object> cgminerDevDetails() {
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
            put("DEVDETAILS", List.of(dev));
            put("DEVS", List.of(dev));
            put("id", 1);
        }};
    }

    public Map<String, Object> cgminerStats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("ID", "STATS");
        s.put("Elapsed", uptimeSeconds());
        s.put("Type", props.getModel());
        s.put("Firmware", props.getFirmware());
        s.put("MAC", macFor());

        return new LinkedHashMap<>() {{
            put("STATUS", List.of(Map.of("STATUS", "S", "Code", 7, "Msg", "Stats", "Description", "cgminer")));
            put("STATS", List.of(s));
            put("id", 1);
        }};
    }
}
