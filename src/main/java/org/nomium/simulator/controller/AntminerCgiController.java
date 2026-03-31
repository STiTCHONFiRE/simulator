package org.nomium.simulator.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.nomium.simulator.service.AntminerStateService;
import org.nomium.simulator.service.RebootService;
import org.nomium.simulator.service.TelemetryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cgi-bin")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AntminerCgiController {

    TelemetryService telemetryService;
    AntminerStateService antState;
    RebootService rebootService;

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

    @GetMapping("/get_system_info.cgi")
    public Map<String, Object> getSystemInfo() {
        return telemetryService.systemInfo();
    }

    @GetMapping("/get_multi_option.cgi")
    public Map<String, Object> getMultiOption() {
        return Map.of("success", true);
    }

    @GetMapping({"/get_miner_status.cgi", "/minerStatus.cgi", "/stats.cgi"})
    public Map<String, Object> minerStatusOld() {
        return telemetryService.antminerStatusOld();
    }

    @GetMapping("/summary.cgi")
    public Map<String, Object> minerSummaryNew() {
        return telemetryService.antminerSummaryNew();
    }
}
