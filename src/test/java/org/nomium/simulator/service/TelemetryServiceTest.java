package org.nomium.simulator.service;

import org.junit.jupiter.api.Test;
import org.nomium.simulator.config.SimProperties;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertEquals("1", modeOptions.get("CurrentMode"));
        assertEquals(3, modeInfo.size());
        assertEquals("ModeName", modeInfo.getFirst().keySet().iterator().next());
        assertEquals("0", modeInfo.getFirst().get("ModeValue"));
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

        assertEquals("1", modeOptions.get("CurrentMode"));
        assertEquals("Normal", modeOptions.get("CurrentModeName"));
        assertEquals(2, modeInfo.size());
        assertEquals("0", modeInfo.get(0).get("ModeValue"));
        assertEquals("1", modeInfo.get(1).get("ModeValue"));
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
        assertEquals("eco", modeInfo.get(0).get("ModeValue"));
        assertEquals("Eco", modeInfo.get(0).get("ModeName"));
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
        assertEquals("2", conf.get("_ant_work_mode"));
        assertEquals("stratum+tcp://a.example:3333", pool.get("URL"));
        assertEquals("2", stats.get("Mode"));
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
    }

    @Test
    void sleepModeForcesZeroHashrateAndIdleTemperaturesEvenWithPools() {
        SimProperties props = new SimProperties();
        props.setIdentitySeed("miner-01");
        props.setIdleTemperatureC(30);
        props.setIdleTemperatureDeltaC(15);
        AntminerStateService state = new AntminerStateService(props);
        TelemetryService telemetryService = new TelemetryService(props, state);

        state.applyForm(Map.of("_ant_work_mode", "sleep"));

        Map<String, Object> summary = firstObject(telemetryService.antminerSummary(), "SUMMARY");

        assertEquals(0.0, number(summary.get("rate_5s")));
        assertEquals(0.0, number(summary.get("rate_avg")));
        assertBetween(number(summary.get("Temperature")), 15, 45);
        assertBetween(number(summary.get("Chip Temp Avg")), 15, 45);
    }

    private static TelemetryService telemetryService(String identitySeed) {
        SimProperties props = new SimProperties();
        props.setIdentitySeed(identitySeed);
        return telemetryService(props);
    }

    private static TelemetryService telemetryService(SimProperties props) {
        return new TelemetryService(props, new AntminerStateService(props));
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
}
