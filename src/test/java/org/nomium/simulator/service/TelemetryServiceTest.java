package org.nomium.simulator.service;

import org.junit.jupiter.api.Test;
import org.nomium.simulator.config.SimProperties;

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

    private static TelemetryService telemetryService(String identitySeed) {
        SimProperties props = new SimProperties();
        props.setIdentitySeed(identitySeed);
        return new TelemetryService(props, new AntminerStateService(props));
    }
}
