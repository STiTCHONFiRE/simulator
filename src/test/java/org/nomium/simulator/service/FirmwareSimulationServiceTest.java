package org.nomium.simulator.service;

import org.junit.jupiter.api.Test;
import org.nomium.simulator.config.SimProperties;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FirmwareSimulationServiceTest {

    @Test
    void uploadAcceptsFirmwareDatafileAndKeepsItPendingUntilRebootCompletes() {
        SimProperties props = new SimProperties();
        props.setFirmware("2025.11");
        props.setSystemFilesystemVersion("2025-11-01");
        FirmwareSimulationService service = new FirmwareSimulationService(props);
        MockMultipartFile datafile = new MockMultipartFile(
                "datafile",
                "Antminer-S19j-Pro-2026.05.tar.gz",
                "application/x-gzip",
                new byte[]{1, 2, 3}
        );

        Map<String, Object> upload = service.acceptUpload(datafile);

        assertEquals("success", upload.get("stats"));
        assertEquals("uploaded", upload.get("status"));
        assertEquals("Antminer-S19j-Pro-2026.05", upload.get("targetFirmware"));
        assertEquals("2025.11", props.getFirmware());

        service.markRebootRequested(System.currentTimeMillis() - 1);
        service.completeIfReady();

        assertEquals("Antminer-S19j-Pro-2026.05", props.getFirmware());
        assertEquals("Antminer-S19j-Pro-2026.05", props.getSystemFilesystemVersion());
        assertEquals("installed", service.status().get("status"));
    }

    @Test
    void uploadRejectsMissingDatafile() {
        FirmwareSimulationService service = new FirmwareSimulationService(new SimProperties());

        assertThrows(ResponseStatusException.class, () -> service.acceptUpload(null));
    }
}
