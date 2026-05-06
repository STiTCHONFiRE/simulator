package org.nomium.simulator.controller;

import org.junit.jupiter.api.Test;
import org.nomium.simulator.config.SimProperties;
import org.nomium.simulator.service.AntminerStateService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiscControllerTest {

    @Test
    void minerPagePointsAgentStockUiParserToMinerJavascript() {
        MiscController controller = controller(new SimProperties());

        String html = controller.minerPage();

        assertTrue(html.contains("src=\"/js/miner.js\""));
    }

    @Test
    void minerJavascriptExposesStockUiModeListShape() {
        MiscController controller = controller(new SimProperties());

        String javascript = controller.minerJavascript();

        assertTrue(javascript.contains("modeList: [{id:0},{id:1},{id:3}]"));
        assertTrue(javascript.contains("this.modeList[0].text = $.i18n.prop(\"modeNormal\")"));
        assertTrue(javascript.contains("this.modeList[1].text = $.i18n.prop(\"modeSleep\")"));
        assertTrue(javascript.contains("this.modeList[2].text = $.i18n.prop(\"modeHEM\")"));
    }

    @Test
    void minerJavascriptUsesConfiguredModeSubset() {
        SimProperties props = new SimProperties();
        props.setModeOptions("sleep,normal");
        MiscController controller = controller(props);

        String javascript = controller.minerJavascript();

        assertTrue(javascript.contains("modeList: [{id:1},{id:0}]"));
        assertFalse(javascript.contains("{id:3}"));
        assertFalse(javascript.contains("$.i18n.prop(\"modeHEM\")"));
    }

    private static MiscController controller(SimProperties props) {
        return new MiscController(props, new AntminerStateService(props));
    }
}
