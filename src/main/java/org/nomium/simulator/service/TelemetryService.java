package org.nomium.simulator.service;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.nomium.simulator.config.SimProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TelemetryService {

    private static final HexFormat MAC_FORMAT = HexFormat.ofDelimiter(":").withUpperCase();
    private static final Pattern ANTMINER_Z_SERIES = Pattern.compile(
            "^\\s*Antminer\\s*Z\\d+",
            Pattern.CASE_INSENSITIVE
    );

    SimProperties props;
    AntminerStateService antState;
    Clock clock;
    Object telemetryLock = new Object();

    @NonFinal
    volatile Instant startedAt;

    @NonFinal
    TelemetryTransition telemetryTransition;

    @Autowired
    public TelemetryService(SimProperties props, AntminerStateService antState) {
        this(props, antState, Clock.systemUTC());
    }

    TelemetryService(SimProperties props, AntminerStateService antState, Clock clock) {
        this.props = props;
        this.antState = antState;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt == null ? clock.instant() : startedAt;
    }

    public long uptimeSeconds() {
        long s = Duration.between(startedAt, clock.instant()).getSeconds();
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
            item.put("ModeName", option.rawName());
            item.put("ModeValue", option.value());
            modeInfo.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("ModeInfo", modeInfo);
        result.put("CurrentMode", snap.workMode());
        result.put("CurrentModeName", antState.modeName(snap.workMode()));
        if (antState.modeDslRuleKey() != null) {
            result.put("DslRuleKey", antState.modeDslRuleKey());
        }
        return result;
    }

    public Map<String, Object> systemInfo() {
        String mac = macFor();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("minertype", props.getModel());
        result.put("miner_type", props.getModel());
        result.put("model", props.getModel());
        result.put("subtype", props.getSubtype());
        result.put("system_filesystem_version", props.getSystemFilesystemVersion());
        result.put("firmware_version", props.getFirmware());
        result.put("nettype", "DHCP");
        result.put("proto", "DHCP");
        result.put("macaddr", mac);
        result.put("mac", mac);
        return result;
    }

    public Map<String, Object> minerType() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("miner_type", props.getModel());
        result.put("minertype", props.getModel());
        result.put("subtype", props.getSubtype());
        result.put("fw_version", props.getFirmware());
        return result;
    }

    public Map<String, Object> chart() {
        Metrics metrics = metrics(antState.snapshot());
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("timestamp", clock.instant().getEpochSecond());
        point.put("rate_5s", metrics.hashrate5s());
        point.put("rate_avg", metrics.hashrateAvg());
        point.put("rate_unit", metrics.rateUnit());
        point.put("temperature", metrics.boardTempC());
        point.put("power", metrics.powerW());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("chart", List.of(point));
        return result;
    }

    public Map<String, Object> antminerStatus() {
        var snap = antState.snapshot();
        var metrics = metrics(snap);
        Map<String, Object> summary = antminerSummaryObject(metrics);
        Map<String, Object> stats = antminerStatsObject(metrics);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("STATUS", List.of(status("Miner status", 11, "antminer")));
        result.put("INFO", minerInfo(metrics));
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
        result.put("INFO", minerInfo(metrics));
        result.put("SUMMARY", List.of(summary));
        result.put("summary", summary);
        return result;
    }

    public Map<String, Object> antminerStats() {
        var metrics = metrics(antState.snapshot());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("STATUS", List.of(status("Stats", 7, "antminer")));
        result.put("INFO", minerInfo(metrics));
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
            dev.put("MHS av", round(legacyMhs(metrics, metrics.hashrateAvg()) / 3.0, 3));
            dev.put("Power", round(metrics.powerW() / 3.0, 2));
            dev.put("Enabled", "Y");
            dev.put("Status", metrics.hashrateAvg() > 0 ? "Alive" : "Idle");
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
        stats.put("ID", metrics.equihash() ? "ZCASH0" : "STATS");
        stats.put("Elapsed", uptimeSeconds());
        stats.put("Type", props.getModel());
        stats.put("Algorithm", metrics.algorithm());
        stats.put("Firmware", props.getFirmware());
        stats.put("Firmware Version", props.getFirmware());
        stats.put("MAC", macFor());
        if (!metrics.equihash()) {
            stats.put("RT", metrics.hashrate5s());
            stats.put("AVG", metrics.hashrateAvg());
        }
        stats.put("GHS 5s", legacyGhs(metrics, metrics.hashrate5s()));
        stats.put("GHS av", legacyGhs(metrics, metrics.hashrateAvg()));
        stats.put("rate_5s", metrics.hashrate5s());
        stats.put("rate_avg", metrics.hashrateAvg());
        stats.put("rate_unit", metrics.rateUnit());
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
        summary.put("MHS 5s", legacyMhs(metrics, metrics.hashrate5s()));
        summary.put("MHS av", legacyMhs(metrics, metrics.hashrateAvg()));
        summary.put("GHS 5s", legacyGhs(metrics, metrics.hashrate5s()));
        summary.put("GHS av", legacyGhs(metrics, metrics.hashrateAvg()));
        summary.put("rate_5s", metrics.hashrate5s());
        summary.put("rate_avg", metrics.hashrateAvg());
        summary.put("rate_unit", metrics.rateUnit());
        summary.put("Algorithm", metrics.algorithm());
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
        summary.put("rate_5s", metrics.hashrate5s());
        summary.put("rate_avg", metrics.hashrateAvg());
        summary.put("rate_30m", metrics.hashrateAvg());
        summary.put("rate_unit", metrics.rateUnit());
        summary.put("ghs5s", legacyGhs(metrics, metrics.hashrate5s()));
        summary.put("ghsav", legacyGhs(metrics, metrics.hashrateAvg()));
        summary.put("GHS 5s", legacyGhs(metrics, metrics.hashrate5s()));
        summary.put("GHS av", legacyGhs(metrics, metrics.hashrateAvg()));
        summary.put("Type", props.getModel());
        summary.put("Algorithm", metrics.algorithm());
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
        stats.put("ID", metrics.equihash() ? "ZCASH0" : "STATS");
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
            tempBase = idleTemperatureBase();
            chipTempOffset = 0.0;
            fanMin = Math.max(300, props.getIdleFanMinRpm());
            fanMax = Math.max(fanMin + 1, props.getIdleFanMaxRpm());
        } else {
            switch (modeKind) {
                case "low" -> {
                    hashFactor = 0.80;
                    powerBase = props.getPowerW() * 0.72;
                    tempBase = Math.max(20, props.getTemperatureC() - 6);
                    chipTempOffset = 8.0;
                    fanMin = 3600;
                    fanMax = 5200;
                }
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

        boolean equihash = isAntminerZSeries();
        double configuredHashrate = equihash ? props.getHashrateKsol() : props.getHashrateThs();
        double hashBase = configuredHashrate * hashFactor;
        TelemetryProfile profile = new TelemetryProfile(
                hashBase,
                powerBase,
                tempBase,
                tempBase + chipTempOffset,
                fanMin,
                fanMax,
                idle
        );
        TelemetryPoint point = smoothTelemetry(profile, clock.instant());

        return new Metrics(
                point.hashrate5s(),
                point.hashrateAvg(),
                point.powerW(),
                point.boardTempC(),
                point.chipTempC(),
                point.fanIn(),
                point.fanOut(),
                point.fan3(),
                point.fan4(),
                mode,
                antState.modeName(mode),
                idle,
                equihash,
                equihash ? "KSol/s" : "TH/s",
                equihash ? "Equihash" : "SHA-256"
        );
    }

    private TelemetryPoint smoothTelemetry(TelemetryProfile profile, Instant now) {
        synchronized (telemetryLock) {
            if (telemetryTransition == null) {
                TelemetryPoint initial = basePoint(profile);
                telemetryTransition = newTransition(profile, initial, now);
                return initial;
            }

            TelemetryPoint current = interpolate(telemetryTransition, now);
            if (!profile.equals(telemetryTransition.profile())) {
                telemetryTransition = newTransition(profile, current, now);
                return interpolate(telemetryTransition, now);
            }

            if (!now.isBefore(telemetryTransition.endsAt())
                    && !now.isBefore(telemetryTransition.hashrateStopsAt())) {
                TelemetryPoint completed = telemetryTransition.target();
                telemetryTransition = newTransition(profile, completed, now);
                return completed;
            }

            return current;
        }
    }

    private TelemetryTransition newTransition(TelemetryProfile profile, TelemetryPoint from, Instant now) {
        return new TelemetryTransition(
                profile,
                from,
                randomTarget(profile),
                now,
                now.plus(randomRampDuration()),
                hashrateStopsAt(profile, from, now)
        );
    }

    private Instant hashrateStopsAt(TelemetryProfile profile, TelemetryPoint from, Instant now) {
        if (!profile.idle() || (from.hashrate5s() <= 0 && from.hashrateAvg() <= 0)) {
            return now;
        }

        Duration delay = props.getHashrateStopDelay();
        if (delay == null || delay.isNegative()) {
            delay = Duration.ofSeconds(5);
        }
        return now.plus(delay);
    }

    private Duration randomRampDuration() {
        long minMillis = durationMillis(props.getTelemetryRampMinDuration(), Duration.ofMinutes(3));
        long maxMillis = durationMillis(props.getTelemetryRampMaxDuration(), Duration.ofMinutes(5));
        if (maxMillis < minMillis) {
            maxMillis = minMillis;
        }

        long millis = minMillis == maxMillis
                ? minMillis
                : ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1);
        return Duration.ofMillis(millis);
    }

    private static long durationMillis(Duration value, Duration fallback) {
        Duration normalized = value == null || value.isNegative() || value.isZero() ? fallback : value;
        return Math.max(1, normalized.toMillis());
    }

    private TelemetryPoint basePoint(TelemetryProfile profile) {
        int fan = profile.fanMin() + (profile.fanMax() - profile.fanMin()) / 2;
        return new TelemetryPoint(
                round(Math.max(0, profile.hashrateBase()), 2),
                round(Math.max(0, profile.hashrateBase()), 2),
                round(Math.max(0, profile.powerBase()), 2),
                round(Math.max(0, profile.boardTempBase()), 2),
                round(Math.max(0, profile.chipTempBase()), 2),
                fan,
                fan,
                fan,
                fan
        );
    }

    private TelemetryPoint randomTarget(TelemetryProfile profile) {
        double hashrate5s = profile.hashrateBase() <= 0
                ? 0
                : shiftByRandomPercent(profile.hashrateBase(), props.getTelemetryJitterPercent(), 0);
        double hashrateAvg = profile.hashrateBase() <= 0
                ? 0
                : shiftByRandomPercent(profile.hashrateBase(), props.getTelemetryJitterPercent(), 0);
        double boardTemp = profile.idle()
                ? idleTemperature(0)
                : shiftByRandomPercent(profile.boardTempBase(), props.getTelemetryJitterPercent(), 5);
        double chipTemp = profile.idle()
                ? idleTemperature(0)
                : shiftByRandomPercent(profile.chipTempBase(), props.getTelemetryJitterPercent(), 5);

        return new TelemetryPoint(
                hashrate5s,
                hashrateAvg,
                shiftByRandomPercent(profile.powerBase(), props.getTelemetryJitterPercent(), 0),
                boardTemp,
                chipTemp,
                randomFan(profile),
                randomFan(profile),
                randomFan(profile),
                randomFan(profile)
        );
    }

    private static int randomFan(TelemetryProfile profile) {
        if (profile.fanMax() <= profile.fanMin()) {
            return profile.fanMin();
        }
        return ThreadLocalRandom.current().nextInt(profile.fanMin(), profile.fanMax());
    }

    private static TelemetryPoint interpolate(TelemetryTransition transition, Instant now) {
        long totalMillis = Math.max(1, Duration.between(transition.startsAt(), transition.endsAt()).toMillis());
        long elapsedMillis = Duration.between(transition.startsAt(), now).toMillis();
        double progress = Math.clamp((double) elapsedMillis / totalMillis, 0.0, 1.0);
        TelemetryPoint from = transition.from();
        TelemetryPoint target = transition.target();
        boolean stopHashrate = transition.profile().idle();
        double hashrate5s = stopHashrate
                ? (now.isBefore(transition.hashrateStopsAt()) ? from.hashrate5s() : target.hashrate5s())
                : lerp(from.hashrate5s(), target.hashrate5s(), progress);
        double hashrateAvg = stopHashrate
                ? (now.isBefore(transition.hashrateStopsAt()) ? from.hashrateAvg() : target.hashrateAvg())
                : lerp(from.hashrateAvg(), target.hashrateAvg(), progress);

        return new TelemetryPoint(
                hashrate5s,
                hashrateAvg,
                lerp(from.powerW(), target.powerW(), progress),
                lerp(from.boardTempC(), target.boardTempC(), progress),
                lerp(from.chipTempC(), target.chipTempC(), progress),
                lerp(from.fanIn(), target.fanIn(), progress),
                lerp(from.fanOut(), target.fanOut(), progress),
                lerp(from.fan3(), target.fan3(), progress),
                lerp(from.fan4(), target.fan4(), progress)
        );
    }

    private static double lerp(double from, double target, double progress) {
        return round(from + (target - from) * progress, 2);
    }

    private static int lerp(int from, int target, double progress) {
        return (int) Math.round(from + (target - from) * progress);
    }

    private boolean isAntminerZSeries() {
        String model = props.getModel();
        return model != null && ANTMINER_Z_SERIES.matcher(model).find();
    }

    private static double legacyGhs(Metrics metrics, double hashrate) {
        return round(metrics.equihash() ? hashrate : hashrate * 1_000.0, 3);
    }

    private static double legacyMhs(Metrics metrics, double hashrate) {
        return round(metrics.equihash() ? hashrate : hashrate * 1_000_000.0, 3);
    }

    private Map<String, Object> minerInfo(Metrics metrics) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("miner_version", props.getFirmware());
        info.put("CompileTime", props.getSystemFilesystemVersion());
        info.put("type", props.getModel());
        info.put("algorithm", metrics.algorithm());
        return info;
    }

    private double idleTemperature(double offset) {
        double delta = Math.max(0, props.getIdleTemperatureDeltaC());
        double base = idleTemperatureBase();
        double min = idleTemperatureMin();
        if (delta == 0) {
            return round(Math.max(min, base + offset), 2);
        }
        return round(Math.max(min, base + offset + ThreadLocalRandom.current().nextDouble(-delta, delta)), 2);
    }

    private double idleTemperatureBase() {
        return Math.max(idleTemperatureMin(), props.getIdleTemperatureC());
    }

    private double idleTemperatureMin() {
        return Math.max(1, props.getIdleTemperatureMinC());
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
        double min = metrics.idle() ? idleTemperatureMin() : 1;
        return round(Math.max(min, base + offset), 2);
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
        status.put("When", clock.instant().getEpochSecond());
        status.put("Code", code);
        status.put("Msg", msg);
        status.put("Description", description);
        return status;
    }

    private record Metrics(
            double hashrate5s,
            double hashrateAvg,
            double powerW,
            double boardTempC,
            double chipTempC,
            int fanIn,
            int fanOut,
            int fan3,
            int fan4,
            String workMode,
            String workModeName,
            boolean idle,
            boolean equihash,
            String rateUnit,
            String algorithm
    ) {
    }

    private record TelemetryProfile(
            double hashrateBase,
            double powerBase,
            double boardTempBase,
            double chipTempBase,
            int fanMin,
            int fanMax,
            boolean idle
    ) {
    }

    private record TelemetryPoint(
            double hashrate5s,
            double hashrateAvg,
            double powerW,
            double boardTempC,
            double chipTempC,
            int fanIn,
            int fanOut,
            int fan3,
            int fan4
    ) {
    }

    private record TelemetryTransition(
            TelemetryProfile profile,
            TelemetryPoint from,
            TelemetryPoint target,
            Instant startsAt,
            Instant endsAt,
            Instant hashrateStopsAt
    ) {
    }
}
