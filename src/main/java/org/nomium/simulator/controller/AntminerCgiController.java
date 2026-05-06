package org.nomium.simulator.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.nomium.simulator.service.AntminerStateService;
import org.nomium.simulator.service.FirmwareSimulationService;
import org.nomium.simulator.service.RebootService;
import org.nomium.simulator.service.TelemetryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
}
