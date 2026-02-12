package org.nomium.sumulator.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.nomium.sumulator.service.TelemetryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cgi-bin")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AntminerCgiController {

    TelemetryService telemetry;

    @GetMapping("/get_miner_conf.cgi")
    public Map<String, Object> getMinerConf(HttpServletRequest req) {
        return telemetry.minerConf(remoteIp(req));
    }

    @GetMapping("/get_system_info.cgi")
    public Map<String, Object> getSystemInfo(HttpServletRequest req) {
        return telemetry.systemInfo(remoteIp(req));
    }

    @GetMapping("/get_multi_option.cgi")
    public Map<String, Object> getMultiOption() {
        return Map.of("success", true);
    }

    //TODO: подумать, должны ли мы отдавать тоже самое тут?
    @GetMapping({"/get_miner_status.cgi", "/minerStatus.cgi", "/stats.cgi"})
    public Map<String, Object> minerStatusOld(HttpServletRequest req) {
        return telemetry.antminerStatusOld(remoteIp(req));
    }

    @GetMapping("/summary.cgi")
    public Map<String, Object> minerSummaryNew(HttpServletRequest req) {
        return telemetry.antminerSummaryNew(remoteIp(req));
    }

    private static String remoteIp(HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        return ip == null ? "0.0.0.0" : ip;
    }

}
