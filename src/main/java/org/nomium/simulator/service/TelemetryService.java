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
import java.util.ArrayList;
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
                pool(snap.p1()),
                pool(snap.p2()),
                pool(snap.p3())
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pools", pools);
        result.put("api-listen", true);
        result.put("api-network", true);
        result.put("vendor", props.getVendor());
        result.put("model", props.getModel());
        result.put("serial", deviceId());

        result.put("_ant_pool1url", snap.p1().getUrl());
        result.put("_ant_pool1user", snap.p1().getUser());
        result.put("_ant_pool1pw", snap.p1().getPw());

        result.put("_ant_pool2url", snap.p2().getUrl());
        result.put("_ant_pool2user", snap.p2().getUser());
        result.put("_ant_pool2pw", snap.p2().getPw());

        result.put("_ant_pool3url", snap.p3().getUrl());
        result.put("_ant_pool3user", snap.p3().getUser());
        result.put("_ant_pool3pw", snap.p3().getPw());

        result.put("_ant_work_mode", snap.workMode());
        result.put("bitmain-work-mode", snap.workMode());
        result.put("bitmain-workmode", snap.workMode());
        result.put("WorkModeValue", snap.workMode());
        result.put("work_mode", snap.workMode());
        result.put("work-mode", snap.workMode());
        result.put("miner-mode", snap.workMode());
        result.put("mode", snap.workMode());
        return result;
    }

    public Map<String, Object> modeOptions() {
        var snap = antState.snapshot();
        List<Map<String, Object>> modeInfo = new ArrayList<>();
        for (AntminerStateService.ModeOption option : antState.modeOptions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ModeName", option.name());
            item.put("ModeValue", option.value());
            modeInfo.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("ModeInfo", modeInfo);
        result.put("CurrentMode", snap.workMode());
        result.put("CurrentModeName", antState.modeName(snap.workMode()));
        return result;
    }

    public Map<String, Object> systemInfo() {
        String mac = macFor();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("minertype", props.getModel());
        result.put("miner_type", props.getModel());
        result.put("model", props.getModel());
        result.put("system_filesystem_version", props.getSystemFilesystemVersion());
        result.put("firmware_version", props.getSystemFilesystemVersion());
        result.put("nettype", "DHCP");
        result.put("proto", "DHCP");
        result.put("macaddr", mac);
        result.put("mac", mac);
        return result;
    }

    public Map<String, Object> antminerStatus() {
        var snap = antState.snapshot();
        var metrics = metrics(snap);
        Map<String, Object> summary = antminerSummaryObject(metrics);
        Map<String, Object> stats = antminerStatsObject(metrics);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("STATUS", List.of(status("Miner status", 11, "antminer")));
        result.put("summary", summary);
        result.put("devs", List.of(antminerLegacyDevObject(metrics)));
        result.put("SUMMARY", List.of(summary));
        result.put("STATS", List.of(stats));
        return result;
    }

    public Map<String, Object> antminerSummary() {
        var metrics = metrics(antState.snapshot());
        Map<String, Object> summary = antminerSummaryObject(metrics);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("STATUS", List.of(status("Summary", 11, "antminer")));
        result.put("SUMMARY", List.of(summary));
        result.put("summary", summary);
        return result;
    }

    public Map<String, Object> antminerStats() {
        var metrics = metrics(antState.snapshot());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("STATUS", List.of(status("Stats", 7, "antminer")));
        result.put("STATS", List.of(antminerStatsObject(metrics)));
        return result;
    }

    public Map<String, Object> cgminerSummary() {
        var snap = antState.snapshot();
        var metrics = metrics(snap);
        Map<String, Object> summary = cgminerSummaryObject(metrics);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("STATUS", List.of(status("Summary", 11, "cgminer")));
        result.put("SUMMARY", List.of(summary));
        result.put("Description", "cgminer");
        result.put("id", 1);
        return result;
    }

    public Map<String, Object> cgminerDevDetails() {
        var metrics = metrics(antState.snapshot());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("ASC", 0);
        details.put("Name", props.getModel());
        details.put("Model", props.getModel());
        details.put("Driver", "Bitmain");
        details.put("Kernel", "cgminer");
        details.put("Type", props.getModel());

        List<Map<String, Object>> devs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> dev = new LinkedHashMap<>();
            dev.put("ASC", i);
            dev.put("Name", props.getModel() + " chain " + (i + 1));
            dev.put("Model", props.getModel());
            dev.put("Temperature", chipTemp(metrics, i - 1));
            dev.put("MHS av", round(metrics.hashrateAvgThs() * 1_000_000.0 / 3.0, 3));
            dev.put("Power", round(metrics.powerW() / 3.0, 2));
            dev.put("Enabled", "Y");
            dev.put("Status", metrics.hashrateAvgThs() > 0 ? "Alive" : "Idle");
            dev.put("Type", props.getModel());
            devs.add(dev);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("STATUS", List.of(status("Dev details", 9, "cgminer")));
        result.put("DEVDETAILS", List.of(details));
        result.put("DEVS", devs);
        result.put("id", 1);
        return result;
    }

    public Map<String, Object> cgminerStats() {
        var metrics = metrics(antState.snapshot());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("ID", "STATS");
        stats.put("Elapsed", uptimeSeconds());
        stats.put("Type", props.getModel());
        stats.put("Firmware", props.getFirmware());
        stats.put("Firmware Version", props.getFirmware());
        stats.put("MAC", macFor());
        stats.put("RT", metrics.hashrate5sThs());
        stats.put("AVG", metrics.hashrateAvgThs());
        stats.put("rate_5s", metrics.hashrate5sThs());
        stats.put("rate_avg", metrics.hashrateAvgThs());
        stats.put("rate_unit", "TH/s");
        stats.put("temp1", metrics.boardTempC());
        stats.put("temp2", round(metrics.boardTempC() + 1.2, 2));
        stats.put("temp3", round(metrics.boardTempC() - 0.8, 2));
        stats.put("fan1", metrics.fanIn());
        stats.put("fan2", metrics.fanOut());
        stats.put("fan3", metrics.fan3());
        stats.put("fan4", metrics.fan4());
        stats.put("Mode", metrics.workMode());
        stats.put("Power Mode", metrics.workMode());
        stats.put("Power", metrics.powerW());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("STATUS", List.of(status("Stats", 7, "cgminer")));
        result.put("STATS", List.of(stats));
        result.put("Description", "cgminer");
        result.put("id", 1);
        return result;
    }

    public Map<String, Object> cgminerPools() {
        var snap = antState.snapshot();
        List<Map<String, Object>> pools = List.of(
                cgminerPool(0, snap.p1()),
                cgminerPool(1, snap.p2()),
                cgminerPool(2, snap.p3())
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("STATUS", List.of(status("Pools", 3, "cgminer")));
        result.put("POOLS", pools);
        result.put("id", 1);
        return result;
    }

    private Map<String, Object> cgminerSummaryObject(Metrics metrics) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("Elapsed", uptimeSeconds());
        summary.put("MHS 5s", round(metrics.hashrate5sThs() * 1_000_000.0, 3));
        summary.put("MHS av", round(metrics.hashrateAvgThs() * 1_000_000.0, 3));
        summary.put("GHS 5s", round(metrics.hashrate5sThs() * 1_000.0, 3));
        summary.put("GHS av", round(metrics.hashrateAvgThs() * 1_000.0, 3));
        summary.put("rate_5s", metrics.hashrate5sThs());
        summary.put("rate_avg", metrics.hashrateAvgThs());
        summary.put("rate_unit", "TH/s");
        summary.put("Power", metrics.powerW());
        summary.put("Temperature", metrics.boardTempC());
        summary.put("Chip Temp Avg", metrics.chipTempC());
        summary.put("Fan Speed In", metrics.fanIn());
        summary.put("Fan Speed Out", metrics.fanOut());
        summary.put("fan1", metrics.fanIn());
        summary.put("fan2", metrics.fanOut());
        summary.put("Power Mode", metrics.workMode());
        summary.put("Mode", metrics.workMode());
        summary.put("Found Blocks", 0);
        summary.put("Accepted", ThreadLocalRandom.current().nextInt(10, 400));
        summary.put("Rejected", ThreadLocalRandom.current().nextInt(0, 4));
        summary.put("Hardware Errors", ThreadLocalRandom.current().nextInt(0, 3));
        summary.put("Type", props.getModel());
        summary.put("Firmware", props.getFirmware());
        summary.put("Firmware Version", props.getFirmware());
        summary.put("CB Platform", props.getModel());
        summary.put("CB Version", props.getSystemFilesystemVersion());
        summary.put("MAC", macFor());
        return summary;
    }

    private Map<String, Object> antminerSummaryObject(Metrics metrics) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("elapsed", uptimeSeconds());
        summary.put("Elapsed", uptimeSeconds());
        summary.put("rate_5s", metrics.hashrate5sThs());
        summary.put("rate_avg", metrics.hashrateAvgThs());
        summary.put("rate_30m", metrics.hashrateAvgThs());
        summary.put("rate_unit", "TH/s");
        summary.put("ghs5s", round(metrics.hashrate5sThs() * 1_000.0, 3));
        summary.put("ghsav", round(metrics.hashrateAvgThs() * 1_000.0, 3));
        summary.put("GHS 5s", round(metrics.hashrate5sThs() * 1_000.0, 3));
        summary.put("GHS av", round(metrics.hashrateAvgThs() * 1_000.0, 3));
        summary.put("Temperature", metrics.boardTempC());
        summary.put("Chip Temp Avg", metrics.chipTempC());
        summary.put("temp1", metrics.boardTempC());
        summary.put("temp2", boardTemp(metrics, 1.2));
        summary.put("temp3", boardTemp(metrics, -0.8));
        summary.put("temp_chip1", metrics.chipTempC());
        summary.put("temp_chip2", chipTemp(metrics, 1.5));
        summary.put("temp_chip3", chipTemp(metrics, -1.1));
        summary.put("fan1", metrics.fanIn());
        summary.put("fan2", metrics.fanOut());
        summary.put("fan3", metrics.fan3());
        summary.put("fan4", metrics.fan4());
        summary.put("Fan Speed In", metrics.fanIn());
        summary.put("Fan Speed Out", metrics.fanOut());
        summary.put("power", metrics.powerW());
        summary.put("Power", metrics.powerW());
        summary.put("Mode", metrics.workMode());
        summary.put("Power Mode", metrics.workMode());
        summary.put("Firmware Version", props.getFirmware());
        summary.put("MAC", macFor());
        return summary;
    }

    private Map<String, Object> antminerStatsObject(Metrics metrics) {
        Map<String, Object> stats = new LinkedHashMap<>(antminerSummaryObject(metrics));
        stats.put("ID", "STATS");
        stats.put("fan", List.of(metrics.fanIn(), metrics.fanOut(), metrics.fan3(), metrics.fan4()));

        List<Map<String, Object>> chains = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double board = boardTemp(metrics, i - 1);
            double chip = chipTemp(metrics, i - 1);

            Map<String, Object> chain = new LinkedHashMap<>();
            chain.put("index", i);
            chain.put("device_board_temp", board);
            chain.put("board_temp", board);
            chain.put("device_chip_temp", chip);
            chain.put("chip_temp", chip);
            chain.put("temp_pcb", List.of(boardTemp(metrics, i - 1.5), board, boardTemp(metrics, i - 0.3)));
            chain.put("temp_chip", List.of(chipTemp(metrics, i - 1.8), chip, chipTemp(metrics, i - 0.4)));
            chains.add(chain);
        }
        stats.put("chain", chains);
        return stats;
    }

    private Map<String, Object> antminerLegacyDevObject(Metrics metrics) {
        Map<String, Object> dev = new LinkedHashMap<>();
        dev.put("elapsed", uptimeSeconds());
        dev.put("freq", "0,temp1=" + metrics.boardTempC()
                + ",temp2=" + boardTemp(metrics, 1.2)
                + ",temp_chip1=" + metrics.chipTempC()
                + ",fan1=" + metrics.fanIn()
                + ",fan2=" + metrics.fanOut());
        dev.put("temp1", metrics.boardTempC());
        dev.put("temp2", boardTemp(metrics, 1.2));
        dev.put("temp3", boardTemp(metrics, -0.8));
        dev.put("temp_chip1", metrics.chipTempC());
        dev.put("temp_chip2", chipTemp(metrics, 1.5));
        dev.put("temp_chip3", chipTemp(metrics, -1.1));
        dev.put("fan1", metrics.fanIn());
        dev.put("fan2", metrics.fanOut());
        dev.put("fan3", metrics.fan3());
        dev.put("fan4", metrics.fan4());
        return dev;
    }

    private Metrics metrics(AntminerStateService.Snapshot snap) {
        String mode = snap.workMode();
        String modeKind = antState.modeKind(mode);
        boolean idle = "sleep".equals(modeKind) || !snap.hasPools();

        double hashFactor;
        double powerBase;
        double tempBase;
        double chipTempOffset;
        int fanMin;
        int fanMax;

        if (idle) {
            hashFactor = 0.0;
            powerBase = Math.max(120, props.getPowerW() * 0.08);
            tempBase = props.getIdleTemperatureC();
            chipTempOffset = 0.0;
            fanMin = 1200;
            fanMax = 2300;
        } else {
            switch (modeKind) {
                case "high" -> {
                    hashFactor = 1.12;
                    powerBase = props.getPowerW() * 1.12;
                    tempBase = props.getTemperatureC() + 5;
                    chipTempOffset = 10.0;
                    fanMin = 5600;
                    fanMax = 7200;
                }
                default -> {
                    hashFactor = 1.0;
                    powerBase = props.getPowerW();
                    tempBase = props.getTemperatureC();
                    chipTempOffset = 10.0;
                    fanMin = 4800;
                    fanMax = 6200;
                }
            }
        }

        double hashBase = props.getHashrateThs() * hashFactor;
        double hashrate5s = hashBase <= 0 ? 0 : shiftByRandomPercent(hashBase, props.getTelemetryJitterPercent(), 0);
        double hashrateAvg = hashBase <= 0 ? 0 : shiftByRandomPercent(hashBase, props.getTelemetryJitterPercent(), 0);
        double boardTemp = idle
                ? idleTemperature(0)
                : shiftByRandomPercent(tempBase, props.getTelemetryJitterPercent(), 5);
        double chipTemp = idle
                ? idleTemperature(chipTempOffset)
                : shiftByRandomPercent(tempBase + chipTempOffset, props.getTelemetryJitterPercent(), 5);
        double power = shiftByRandomPercent(powerBase, props.getTelemetryJitterPercent(), 0);

        return new Metrics(
                hashrate5s,
                hashrateAvg,
                power,
                boardTemp,
                chipTemp,
                ThreadLocalRandom.current().nextInt(fanMin, fanMax),
                ThreadLocalRandom.current().nextInt(fanMin, fanMax),
                ThreadLocalRandom.current().nextInt(fanMin, fanMax),
                ThreadLocalRandom.current().nextInt(fanMin, fanMax),
                mode,
                antState.modeName(mode),
                idle
        );
    }

    private double idleTemperature(double offset) {
        double delta = Math.max(0, props.getIdleTemperatureDeltaC());
        return round(Math.max(0, props.getIdleTemperatureC() + offset + ThreadLocalRandom.current().nextDouble(-delta, delta)), 2);
    }

    private double shiftByRandomPercent(double base, double maxPercent, double min) {
        double percent = Math.max(0, maxPercent);
        if (percent == 0) {
            return round(Math.max(min, base), 2);
        }
        double factor = 1 + ThreadLocalRandom.current().nextDouble(-percent, percent) / 100.0;
        return round(Math.max(min, base * factor), 2);
    }

    private double boardTemp(Metrics metrics, double offset) {
        return tempWithOffset(metrics, metrics.boardTempC(), offset);
    }

    private double chipTemp(Metrics metrics, double offset) {
        return tempWithOffset(metrics, metrics.chipTempC(), offset);
    }

    private double tempWithOffset(Metrics metrics, double base, double offset) {
        double value = round(base + offset, 2);
        if (!metrics.idle()) {
            return value;
        }

        double delta = Math.max(0, props.getIdleTemperatureDeltaC());
        double min = Math.max(0, props.getIdleTemperatureC() - delta);
        double max = props.getIdleTemperatureC() + delta;
        return round(Math.max(min, Math.min(max, value)), 2);
    }

    private static Map<String, Object> pool(AntminerStateService.Pool pool) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", pool.getUrl());
        result.put("user", pool.getUser());
        result.put("pass", pool.getPw());
        return result;
    }

    private static Map<String, Object> cgminerPool(int index, AntminerStateService.Pool pool) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("POOL", index);
        result.put("URL", pool.getUrl());
        result.put("User", pool.getUser());
        result.put("Pass", pool.getPw());
        result.put("Status", "Alive");
        return result;
    }

    private Map<String, Object> status(String msg, int code, String description) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("STATUS", "S");
        status.put("When", Instant.now().getEpochSecond());
        status.put("Code", code);
        status.put("Msg", msg);
        status.put("Description", description);
        return status;
    }

    private record Metrics(
            double hashrate5sThs,
            double hashrateAvgThs,
            double powerW,
            double boardTempC,
            double chipTempC,
            int fanIn,
            int fanOut,
            int fan3,
            int fan4,
            String workMode,
            String workModeName,
            boolean idle
    ) {
    }
}
