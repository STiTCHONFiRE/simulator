package org.nomium.simulator.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.nomium.simulator.service.AntminerStateService;
import org.nomium.simulator.service.RebootService;
import org.nomium.simulator.service.TelemetryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

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
    public Map<String, Object> getMinerConf(HttpServletRequest req) {
        return telemetryService.minerConf(localIp(req));
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
    public Map<String, Object> getSystemInfo(HttpServletRequest req) {
        return telemetryService.systemInfo(localIp(req));
    }

    @GetMapping("/get_multi_option.cgi")
    public Map<String, Object> getMultiOption() {
        return Map.of("success", true);
    }

    //TODO: подумать, должны ли мы отдавать тоже самое тут?
    @GetMapping({"/get_miner_status.cgi", "/minerStatus.cgi", "/stats.cgi"})
    public Map<String, Object> minerStatusOld(HttpServletRequest req) {
        return telemetryService.antminerStatusOld(localIp(req));
    }

    @GetMapping("/summary.cgi")
    public Map<String, Object> minerSummaryNew(HttpServletRequest req) {
        return telemetryService.antminerSummaryNew(localIp(req));
    }

    private static String localIp(HttpServletRequest req) {
        String ip = req.getLocalAddr();
        return ip == null ? "0.0.0.0" : ip;
    }

}
