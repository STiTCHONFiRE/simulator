package org.nomium.simulator.service;

import org.junit.jupiter.api.Test;
import org.nomium.simulator.config.SimProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryServiceTest {

    @Test
    void macForReturnsStableLocallyAdministeredUnicastAddress() {
        TelemetryService telemetryService = telemetryService("miner-01");
        String mac = telemetryService.macFor();

        assertEquals("66:92:2F:86:B1:6A", mac);
        assertEquals(mac, telemetryService.macFor());
        assertTrue(mac.matches("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$"));

        int firstOctet = Integer.parseInt(mac.substring(0, 2), 16);
        assertEquals(0, firstOctet & 0x01, "MAC must be unicast");
        assertEquals(0x02, firstOctet & 0x02, "MAC must be locally administered");
    }

    @Test
    void macForReturnsDifferentAddressForDifferentIdentitySeed() {
        assertNotEquals(
                telemetryService("miner-01").macFor(),
                telemetryService("miner-02").macFor()
        );
    }

    @Test
    void deviceIdUsesIdentitySeed() {
        assertEquals("SIM-426947", telemetryService("miner-01").deviceId());
        assertNotEquals(
                telemetryService("miner-01").deviceId(),
                telemetryService("miner-02").deviceId()
        );
    }

    @Test
    void modeOptionsExposeAgentCompatibleModeInfo() {
        TelemetryService telemetryService = telemetryService("miner-01");

        Map<String, Object> modeOptions = telemetryService.modeOptions();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modeInfo = (List<Map<String, Object>>) modeOptions.get("ModeInfo");

        assertEquals("0", modeOptions.get("CurrentMode"));
        assertEquals("modeNormal", modeOptions.get("CurrentModeName"));
        assertEquals(3, modeInfo.size());
        assertEquals("ModeName", modeInfo.getFirst().keySet().iterator().next());
        assertEquals("0", modeInfo.getFirst().get("ModeValue"));
        assertEquals("modeNormal", modeInfo.getFirst().get("ModeName"));
    }

    @Test
    void cgminerPoolsExposeConfiguredPoolValues() {
        TelemetryService telemetryService = telemetryService("miner-01");

        Map<String, Object> response = telemetryService.cgminerPools();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pools = (List<Map<String, Object>>) response.get("POOLS");

        assertEquals(3, pools.size());
        assertEquals("stratum+tcp://pool.example.com:3333", pools.getFirst().get("URL"));
        assertEquals("worker1", pools.getFirst().get("User"));
        assertEquals("x", pools.getFirst().get("Pass"));
    }

    @Test
    void modeOptionsCanBeReducedFromConfiguration() {
        SimProperties props = new SimProperties();
        props.setIdentitySeed("miner-01");
        props.setModeOptions("sleep,normal");
        props.setDefaultWorkMode("high");
        TelemetryService telemetryService = telemetryService(props);

        Map<String, Object> modeOptions = telemetryService.modeOptions();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modeInfo = (List<Map<String, Object>>) modeOptions.get("ModeInfo");

        assertEquals("0", modeOptions.get("CurrentMode"));
        assertEquals("modeNormal", modeOptions.get("CurrentModeName"));
        assertEquals(2, modeInfo.size());
        assertEquals("1", modeInfo.get(0).get("ModeValue"));
        assertEquals("modeSleep", modeInfo.get(0).get("ModeName"));
        assertEquals("0", modeInfo.get(1).get("ModeValue"));
        assertEquals("modeNormal", modeInfo.get(1).get("ModeName"));
    }

    @Test
    void modeOptionsSupportExplicitRawValueAndLabelPairs() {
        SimProperties props = new SimProperties();
        props.setIdentitySeed("miner-01");
        props.setModeOptions("eco:Eco,standard:Standard,turbo:Turbo");
        props.setDefaultWorkMode("turbo");
        TelemetryService telemetryService = telemetryService(props);

        Map<String, Object> modeOptions = telemetryService.modeOptions();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modeInfo = (List<Map<String, Object>>) modeOptions.get("ModeInfo");

        assertEquals("turbo", modeOptions.get("CurrentMode"));
        assertEquals("Turbo", modeOptions.get("CurrentModeName"));
        assertEquals("eco", modeInfo.getFirst().get("ModeValue"));
        assertEquals("Eco", modeInfo.getFirst().get("ModeName"));
    }

    @Test
    void telemetryUsesConfiguredPercentShiftForActiveMining() {
        SimProperties props = new SimProperties();
        props.setIdentitySeed("miner-01");
        props.setHashrateThs(100);
        props.setTemperatureC(60);
        props.setTelemetryJitterPercent(2);
        TelemetryService telemetryService = telemetryService(props);

        Map<String, Object> summary = firstObject(telemetryService.antminerSummary(), "SUMMARY");

        assertBetween(number(summary.get("rate_5s")), 98, 102);
        assertBetween(number(summary.get("rate_avg")), 98, 102);
        assertBetween(number(summary.get("Temperature")), 58.8, 61.2);
        assertBetween(number(summary.get("Chip Temp Avg")), 68.6, 71.4);
        assertEquals("TH/s", summary.get("rate_unit"));
        assertEquals("SHA-256", summary.get("Algorithm"));
    }

    @Test
    void telemetryRemainsStableForRepeatedRequestsAtTheSameInstant() {
        SimProperties props = new SimProperties();
        props.setHashrateThs(100);
        props.setTemperatureC(60);
        props.setTelemetryJitterPercent(2);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        TelemetryService telemetryService = telemetryService(props, clock);

        Map<String, Object> first = firstObject(telemetryService.antminerSummary(), "SUMMARY");
        Map<String, Object> second = firstObject(telemetryService.antminerSummary(), "SUMMARY");

        assertEquals(first.get("rate_5s"), second.get("rate_5s"));
        assertEquals(first.get("rate_avg"), second.get("rate_avg"));
        assertEquals(first.get("Temperature"), second.get("Temperature"));
        assertEquals(first.get("Chip Temp Avg"), second.get("Chip Temp Avg"));
        assertEquals(first.get("power"), second.get("power"));
        assertEquals(first.get("fan1"), second.get("fan1"));
    }

    @Test
    void sleepStopsHashrateAfterDelayWhileOtherTelemetryRampsDownAndUp() {
        SimProperties props = new SimProperties();
        props.setHashrateThs(100);
        props.setPowerW(3000);
        props.setTemperatureC(60);
        props.setIdleTemperatureC(30);
        props.setIdleTemperatureDeltaC(0);
        props.setTelemetryJitterPercent(0);
        props.setTelemetryRampMinDuration(Duration.ofMinutes(4));
        props.setTelemetryRampMaxDuration(Duration.ofMinutes(4));
        props.setHashrateStopDelay(Duration.ofSeconds(5));
        AntminerStateService state = new AntminerStateService(props);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        TelemetryService telemetryService = new TelemetryService(props, state, clock);

        Map<String, Object> normal = firstObject(telemetryService.antminerSummary(), "SUMMARY");
        assertEquals(100.0, number(normal.get("rate_5s")));
        assertEquals(60.0, number(normal.get("Temperature")));
        assertEquals(70.0, number(normal.get("Chip Temp Avg")));
        assertEquals(3000.0, number(normal.get("power")));
        assertEquals(5500, ((Number) normal.get("fan1")).intValue());

        state.applyForm(Map.of("_ant_work_mode", "1"));
        Map<String, Object> sleepStart = firstObject(telemetryService.antminerSummary(), "SUMMARY");
        assertEquals(100.0, number(sleepStart.get("rate_5s")));
        assertEquals(60.0, number(sleepStart.get("Temperature")));
        assertEquals("1", sleepStart.get("Mode"));

        clock.advance(Duration.ofSeconds(4));
        Map<String, Object> beforeHashrateStop = firstObject(telemetryService.antminerSummary(), "SUMMARY");
        assertEquals(100.0, number(beforeHashrateStop.get("rate_5s")));
        assertTrue(number(beforeHashrateStop.get("Temperature")) < 60.0);

        clock.advance(Duration.ofSeconds(1));
        Map<String, Object> afterHashrateStop = firstObject(telemetryService.antminerSummary(), "SUMMARY");
        assertEquals(0.0, number(afterHashrateStop.get("rate_5s")));
        assertEquals(0.0, number(afterHashrateStop.get("rate_avg")));
        assertTrue(number(afterHashrateStop.get("Temperature")) > 30.0);

        clock.advance(Duration.ofSeconds(115));
        Map<String, Object> sleepHalfway = firstObject(telemetryService.antminerSummary(), "SUMMARY");
        assertEquals(0.0, number(sleepHalfway.get("rate_5s")));
        assertEquals(45.0, number(sleepHalfway.get("Temperature")));
        assertEquals(50.0, number(sleepHalfway.get("Chip Temp Avg")));
        assertEquals(1620.0, number(sleepHalfway.get("power")));
        assertBetween(number(sleepHalfway.get("fan1")), 3350, 3900);

        clock.advance(Duration.ofMinutes(2));
        Map<String, Object> sleeping = firstObject(telemetryService.antminerSummary(), "SUMMARY");
        assertEquals(0.0, number(sleeping.get("rate_5s")));
        assertEquals(30.0, number(sleeping.get("Temperature")));
        assertEquals(30.0, number(sleeping.get("Chip Temp Avg")));
        assertEquals(240.0, number(sleeping.get("power")));
        assertBetween(number(sleeping.get("fan1")), 1200, 2299);

        state.applyForm(Map.of("_ant_work_mode", "0"));
        firstObject(telemetryService.antminerSummary(), "SUMMARY");
        clock.advance(Duration.ofMinutes(2));
        Map<String, Object> normalHalfway = firstObject(telemetryService.antminerSummary(), "SUMMARY");
        assertEquals(50.0, number(normalHalfway.get("rate_5s")));
        assertEquals(45.0, number(normalHalfway.get("Temperature")));
        assertEquals(50.0, number(normalHalfway.get("Chip Temp Avg")));
        assertEquals(1620.0, number(normalHalfway.get("power")));

        clock.advance(Duration.ofMinutes(2));
        Map<String, Object> normalAgain = firstObject(telemetryService.antminerSummary(), "SUMMARY");
        assertEquals(100.0, number(normalAgain.get("rate_5s")));
        assertEquals(60.0, number(normalAgain.get("Temperature")));
        assertEquals(70.0, number(normalAgain.get("Chip Temp Avg")));
        assertEquals(3000.0, number(normalAgain.get("power")));
        assertBetween(number(normalAgain.get("fan1")), 4800, 6199);
    }

    @Test
    void emptyPoolsStopHashrateAfterTheSameDelay() {
        SimProperties props = new SimProperties();
        props.setHashrateThs(100);
        props.setTelemetryJitterPercent(0);
        props.setTelemetryRampMinDuration(Duration.ofMinutes(4));
        props.setTelemetryRampMaxDuration(Duration.ofMinutes(4));
        props.setHashrateStopDelay(Duration.ofSeconds(5));
        AntminerStateService state = new AntminerStateService(props);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        TelemetryService telemetryService = new TelemetryService(props, state, clock);

        firstObject(telemetryService.antminerSummary(), "SUMMARY");
        state.applyForm(Map.of(
                "_ant_pool1url", "",
                "_ant_pool2url", "",
                "_ant_pool3url", ""
        ));
        firstObject(telemetryService.antminerSummary(), "SUMMARY");

        clock.advance(Duration.ofSeconds(4));
        Map<String, Object> beforeStop = firstObject(telemetryService.antminerSummary(), "SUMMARY");
        assertEquals(100.0, number(beforeStop.get("rate_5s")));

        clock.advance(Duration.ofSeconds(1));
        Map<String, Object> stopped = firstObject(telemetryService.antminerSummary(), "SUMMARY");
        assertEquals(0.0, number(stopped.get("rate_5s")));
        assertEquals(0.0, number(stopped.get("rate_avg")));
        assertTrue(number(stopped.get("Temperature")) > props.getIdleTemperatureC());
        assertEquals("0", stopped.get("Mode"));
    }

    @Test
    void sleepKeepsTemperatureAndFansRunningAtReducedNonZeroValues() {
        SimProperties props = new SimProperties();
        props.setDefaultWorkMode("sleep");
        props.setIdleTemperatureC(0);
        props.setIdleTemperatureDeltaC(0);
        props.setIdleTemperatureMinC(12);
        props.setIdleFanMinRpm(900);
        props.setIdleFanMaxRpm(1500);
        props.setTelemetryJitterPercent(0);
        TelemetryService telemetryService = telemetryService(props);

        Map<String, Object> summary = firstObject(telemetryService.antminerSummary(), "SUMMARY");

        assertEquals(0.0, number(summary.get("rate_5s")));
        assertEquals(12.0, number(summary.get("Temperature")));
        assertEquals(12.0, number(summary.get("Chip Temp Avg")));
        assertEquals(12.0, number(summary.get("temp3")));
        assertEquals(12.0, number(summary.get("temp_chip3")));
        assertEquals(1200, ((Number) summary.get("fan1")).intValue());
        assertEquals(1200, ((Number) summary.get("fan2")).intValue());
        assertEquals(1200, ((Number) summary.get("fan3")).intValue());
        assertEquals(1200, ((Number) summary.get("fan4")).intValue());
        assertEquals("1", summary.get("Mode"));
    }

    @Test
    void antminerZSeriesUsesKsolTelemetryAndEquihashMarkers() {
        SimProperties props = new SimProperties();
        props.setIdentitySeed("z15-pro-01");
        props.setModel("Antminer Z15 Pro");
        props.setHashrateKsol(840);
        props.setTelemetryJitterPercent(0);
        TelemetryService telemetryService = telemetryService(props);

        Map<String, Object> httpSummary = firstObject(telemetryService.antminerSummary(), "SUMMARY");
        Map<String, Object> httpStats = firstObject(telemetryService.antminerStats(), "STATS");
        Map<String, Object> tcpSummary = firstObject(telemetryService.cgminerSummary(), "SUMMARY");
        Map<String, Object> tcpStats = firstObject(telemetryService.cgminerStats(), "STATS");
        Map<String, Object> chart = firstObject(telemetryService.chart(), "chart");

        assertEquals(840.0, number(httpSummary.get("rate_5s")));
        assertEquals(840.0, number(httpSummary.get("rate_avg")));
        assertEquals("KSol/s", httpSummary.get("rate_unit"));
        assertEquals("Antminer Z15 Pro", httpSummary.get("Type"));
        assertEquals("Equihash", httpSummary.get("Algorithm"));
        assertEquals("ZCASH0", httpStats.get("ID"));

        assertEquals(840.0, number(tcpSummary.get("GHS 5s")));
        assertEquals("KSol/s", tcpSummary.get("rate_unit"));
        assertEquals("ZCASH0", tcpStats.get("ID"));
        assertEquals(840.0, number(tcpStats.get("GHS 5s")));
        assertEquals("KSol/s", tcpStats.get("rate_unit"));
        assertFalse(tcpStats.containsKey("RT"), "Equihash RT would be interpreted as TH/s by the agent");
        assertFalse(tcpStats.containsKey("AVG"), "Equihash AVG would be interpreted as TH/s by the agent");

        assertEquals(840.0, number(chart.get("rate_5s")));
        assertEquals("KSol/s", chart.get("rate_unit"));
    }

    @Test
    void antminerZSeriesKeepsDslLowPowerModeAndKsolUnit() {
        SimProperties props = new SimProperties();
        props.setIdentitySeed("z15-pro-low");
        props.setModel("Antminer Z15 Pro");
        props.setHashrateKsol(840);
        props.setPowerW(2560);
        props.setTelemetryJitterPercent(0);
        props.getModeDsl().setRuleKey("bitmain.low-power3");
        props.setDefaultWorkMode("low");
        TelemetryService telemetryService = telemetryService(props);

        Map<String, Object> summary = firstObject(telemetryService.antminerSummary(), "SUMMARY");

        assertEquals(672.0, number(summary.get("rate_avg")));
        assertEquals("KSol/s", summary.get("rate_unit"));
        assertEquals(1843.2, number(summary.get("power")));
        assertEquals("3", summary.get("Mode"));
    }

    @Test
    void applyingPoolsAndModeChangesReportedConfigAndTelemetryMode() {
        SimProperties props = new SimProperties();
        props.setIdentitySeed("miner-01");
        AntminerStateService state = new AntminerStateService(props);
        TelemetryService telemetryService = new TelemetryService(props, state);

        state.applyForm(Map.of(
                "_ant_pool1url", "stratum+tcp://a.example:3333",
                "_ant_pool1user", "alice.worker",
                "_ant_pool1pw", "secret",
                "_ant_pool2url", "",
                "_ant_pool2user", "",
                "_ant_pool2pw", "",
                "_ant_work_mode", "high"
        ));

        Map<String, Object> conf = telemetryService.minerConf();
        Map<String, Object> pool = firstObject(telemetryService.cgminerPools(), "POOLS");
        Map<String, Object> stats = firstObject(telemetryService.antminerStats(), "STATS");

        assertEquals("stratum+tcp://a.example:3333", conf.get("_ant_pool1url"));
        assertEquals("alice.worker", conf.get("_ant_pool1user"));
        assertEquals("secret", conf.get("_ant_pool1pw"));
        assertEquals("3", conf.get("_ant_work_mode"));
        assertEquals("stratum+tcp://a.example:3333", pool.get("URL"));
        assertEquals("3", stats.get("Mode"));
    }

    @Test
    void noPoolsForceZeroHashrateAndIdleTemperatures() {
        SimProperties props = new SimProperties();
        props.setIdentitySeed("miner-01");
        props.setIdleTemperatureC(30);
        props.setIdleTemperatureDeltaC(15);
        AntminerStateService state = new AntminerStateService(props);
        TelemetryService telemetryService = new TelemetryService(props, state);

        state.applyForm(Map.of(
                "_ant_pool1url", "",
                "_ant_pool1user", "",
                "_ant_pool1pw", "",
                "_ant_pool2url", "",
                "_ant_pool2user", "",
                "_ant_pool2pw", "",
                "_ant_pool3url", "",
                "_ant_pool3user", "",
                "_ant_pool3pw", ""
        ));

        Map<String, Object> summary = firstObject(telemetryService.antminerSummary(), "SUMMARY");

        assertEquals(0.0, number(summary.get("rate_5s")));
        assertEquals(0.0, number(summary.get("rate_avg")));
        assertBetween(number(summary.get("Temperature")), 15, 45);
        assertBetween(number(summary.get("Chip Temp Avg")), 15, 45);
        assertBetween(number(summary.get("fan1")), 1200, 2299);
        assertBetween(number(summary.get("fan2")), 1200, 2299);
    }

    @Test
    void sleepModeForcesZeroHashrateAndIdleTemperaturesEvenWithPools() {
        SimProperties props = new SimProperties();
        props.setIdentitySeed("miner-01");
        props.setIdleTemperatureC(30);
        props.setIdleTemperatureDeltaC(15);
        AntminerStateService state = new AntminerStateService(props);
        TelemetryService telemetryService = new TelemetryService(props, state);

        state.applyForm(Map.of("_ant_work_mode", "1"));

        Map<String, Object> summary = firstObject(telemetryService.antminerSummary(), "SUMMARY");
        Map<String, Object> stats = firstObject(telemetryService.antminerStats(), "STATS");

        assertEquals(0.0, number(summary.get("rate_5s")));
        assertEquals(0.0, number(summary.get("rate_avg")));
        assertEquals("1", stats.get("Mode"));
        assertBetween(number(summary.get("Temperature")), 15, 45);
        assertBetween(number(summary.get("Chip Temp Avg")), 15, 45);
        assertBetween(number(summary.get("fan1")), 1200, 2299);
        assertBetween(number(summary.get("fan2")), 1200, 2299);
    }

    @Test
    void dslLowPowerModeRemainsActiveAndUsesReducedTelemetry() {
        SimProperties props = new SimProperties();
        props.setIdentitySeed("miner-01");
        props.getModeDsl().setRuleKey("bitmain.low-power3");
        props.setDefaultWorkMode("low");
        props.setHashrateThs(100);
        props.setPowerW(3000);
        props.setTelemetryJitterPercent(0);
        TelemetryService telemetryService = telemetryService(props);

        Map<String, Object> summary = firstObject(telemetryService.antminerSummary(), "SUMMARY");

        assertEquals(80.0, number(summary.get("rate_avg")));
        assertEquals(2160.0, number(summary.get("power")));
        assertEquals("3", summary.get("Mode"));
    }

    private static TelemetryService telemetryService(String identitySeed) {
        SimProperties props = new SimProperties();
        props.setIdentitySeed(identitySeed);
        return telemetryService(props);
    }

    private static TelemetryService telemetryService(SimProperties props) {
        return new TelemetryService(props, new AntminerStateService(props));
    }

    private static TelemetryService telemetryService(SimProperties props, Clock clock) {
        return new TelemetryService(props, new AntminerStateService(props), clock);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstObject(Map<String, Object> response, String key) {
        return ((List<Map<String, Object>>) response.get(key)).getFirst();
    }

    private static double number(Object value) {
        return ((Number) value).doubleValue();
    }

    private static void assertBetween(double actual, double min, double max) {
        assertTrue(actual >= min && actual <= max, "expected " + actual + " between " + min + " and " + max);
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return zone.equals(ZoneOffset.UTC) ? this : Clock.fixed(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
