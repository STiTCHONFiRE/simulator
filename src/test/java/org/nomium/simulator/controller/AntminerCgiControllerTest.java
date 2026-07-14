package org.nomium.simulator.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nomium.simulator.config.SimProperties;
import org.nomium.simulator.service.AntminerStateService;
import org.nomium.simulator.service.FirmwareSimulationService;
import org.nomium.simulator.service.RebootService;
import org.nomium.simulator.service.TelemetryService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AntminerCgiControllerTest {

    private AntminerStateService state;
    private TelemetryService telemetry;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SimProperties props = new SimProperties();
        props.setIdentitySeed("contract-test");
        state = new AntminerStateService(props);
        telemetry = new TelemetryService(props, state);
        FirmwareSimulationService firmware = new FirmwareSimulationService(props);
        RebootService reboot = new RebootService(props, telemetry, firmware);
        mockMvc = standaloneSetup(new AntminerCgiController(telemetry, state, reboot, firmware)).build();
    }

    @Test
    void acceptsModernJsonConfigPayloadProducedByCurrentAgent() throws Exception {
        mockMvc.perform(post("/cgi-bin/set_miner_conf.cgi")
                        .contentType("application/json")
                        .content("""
                                {
                                  "miner-mode": "1",
                                  "pools": [
                                    {"url":"stratum+tcp://one.example:3333","user":"one.worker","pass":"a"},
                                    {"url":"","user":"","pass":""},
                                    {"url":"stratum+tcp://three.example:3333","user":"three.worker","pass":"c"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Map<String, Object> config = telemetry.minerConf();
        assertEquals("1", config.get("miner-mode"));
        assertEquals("stratum+tcp://one.example:3333", config.get("_ant_pool1url"));
        assertEquals("", config.get("_ant_pool2url"));
        assertEquals("stratum+tcp://three.example:3333", config.get("_ant_pool3url"));
    }

    @Test
    void supportsModernAndLegacyBlinkContracts() throws Exception {
        mockMvc.perform(post("/cgi-bin/blink.cgi")
                        .contentType("application/json")
                        .content("{\"blink\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blink").value(true));

        mockMvc.perform(get("/cgi-bin/get_blink_status.cgi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blink").value(true));

        mockMvc.perform(post("/cgi-bin/blink.cgi")
                        .contentType("application/x-www-form-urlencoded")
                        .param("action", "stopBlink"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blink").value(false));
    }

    @Test
    void exposesMinerTypeFactsRequiredByBmuFirmwareFlow() throws Exception {
        mockMvc.perform(get("/cgi-bin/miner_type.cgi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.miner_type").value("Antminer S19j Pro"))
                .andExpect(jsonPath("$.subtype").value("AMLCtrl_BHB42XXX"))
                .andExpect(jsonPath("$.fw_version").value("2025.11"));
    }

    @Test
    void jsonPoolsCanAlsoBeAppliedDirectlyAsAgentShape() {
        state.applyJson(Map.of(
                "bitmain-work-mode", 3,
                "pools", List.of(Map.of("url", "stratum+tcp://json.example:3333", "user", "worker", "pass", "x"))
        ));

        assertEquals("3", state.snapshot().workMode());
        assertEquals("stratum+tcp://json.example:3333", state.snapshot().p1().getUrl());
    }
}
