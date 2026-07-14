package org.nomium.simulator.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.nomium.simulator.service.AntminerStateService;
import org.nomium.simulator.service.FirmwareSimulationService;
import org.nomium.simulator.service.RebootService;
import org.nomium.simulator.service.TelemetryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cgi-bin")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AntminerCgiController {

    TelemetryService telemetryService;
    AntminerStateService antState;
    RebootService rebootService;
    FirmwareSimulationService firmwareSimulationService;

    @GetMapping("/get_miner_conf.cgi")
    public Map<String, Object> getMinerConf() {
        return telemetryService.minerConf();
    }

    @PostMapping(value = "/set_miner_conf.cgi", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> setMinerConf(@RequestParam Map<String, String> form) {
        antState.applyForm(form);
        return Map.of("success", true);
    }

    @PostMapping(value = "/set_miner_conf.cgi", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> setMinerConfJson(@RequestBody Map<String, Object> config) {
        antState.applyJson(config);
        return Map.of("success", true);
    }

    @GetMapping("/reboot.cgi")
    public Map<String, Object> reboot() {
        rebootService.requestReboot();
        return Map.of("success", true);
    }

    @PostMapping(value = "/upgrade.cgi", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upgrade(@RequestParam("datafile") MultipartFile datafile) {
        return firmwareSimulationService.acceptUpload(datafile);
    }

    @GetMapping("/upgrade_status.cgi")
    public Map<String, Object> upgradeStatus() {
        return firmwareSimulationService.status();
    }

    @GetMapping("/get_system_info.cgi")
    public Map<String, Object> getSystemInfo() {
        return telemetryService.systemInfo();
    }

    @GetMapping("/miner_type.cgi")
    public Map<String, Object> getMinerType() {
        return telemetryService.minerType();
    }

    @GetMapping("/get_multi_option.cgi")
    public Map<String, Object> getMultiOption() {
        return telemetryService.modeOptions();
    }

    @GetMapping({"/get_miner_status.cgi", "/minerStatus.cgi"})
    public Map<String, Object> minerStatus() {
        return telemetryService.antminerStatus();
    }

    @GetMapping("/summary.cgi")
    public Map<String, Object> minerSummary() {
        return telemetryService.antminerSummary();
    }

    @GetMapping("/stats.cgi")
    public Map<String, Object> minerStats() {
        return telemetryService.antminerStats();
    }

    @GetMapping("/chart.cgi")
    public Map<String, Object> chart() {
        return telemetryService.chart();
    }

    @GetMapping("/get_blink_status.cgi")
    public Map<String, Object> getBlinkStatus() {
        return Map.of("blink", antState.blink());
    }

    @PostMapping(value = "/blink.cgi", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> setBlinkJson(@RequestBody Map<String, Object> request) {
        Object value = request == null ? null : request.get("blink");
        boolean enabled = parseBlink(value);
        antState.setBlink(enabled);
        return Map.of("success", true, "blink", enabled);
    }

    @PostMapping(value = "/blink.cgi", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> setBlinkForm(@RequestParam("action") String action) {
        boolean enabled;
        if ("startBlink".equalsIgnoreCase(action)) {
            enabled = true;
        } else if ("stopBlink".equalsIgnoreCase(action)) {
            enabled = false;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported blink action");
        }

        antState.setBlink(enabled);
        return Map.of("success", true, "blink", enabled);
    }

    private static boolean parseBlink(Object value) {
        if (value instanceof Boolean enabled) {
            return enabled;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                return false;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "blink must be a boolean");
    }
}
