package org.nomium.simulator.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.nomium.simulator.config.SimProperties;
import org.nomium.simulator.service.AntminerStateService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MiscController {

    SimProperties props;
    AntminerStateService antState;

    @GetMapping(value = {"/", "/test", "test.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public String index() {
        return "<html><body><h1>" + props.getVendor() + " " + props.getModel() + "</h1>"
                + "<p>Firmware: " + props.getFirmware() + "</p></body></html>";
    }

    @GetMapping(value = "/miner.html", produces = MediaType.TEXT_HTML_VALUE)
    public String minerPage() {
        return """
                <!doctype html>
                <html>
                <head>
                    <title>Miner Configuration</title>
                    <script src="/js/miner.js"></script>
                </head>
                <body>
                    <div id="miner-mode"></div>
                </body>
                </html>
                """;
    }

    @GetMapping(value = "/js/miner.js", produces = "application/javascript")
    public String minerJavascript() {
        StringBuilder modes = new StringBuilder();
        var modeOptions = antState.modeOptions();
        for (int i = 0; i < modeOptions.size(); i++) {
            var option = modeOptions.get(i);
            if (i > 0) {
                modes.append(",");
            }
            modes.append("{id:").append(jsValue(option.value())).append("}");
        }

        StringBuilder labels = new StringBuilder();
        for (int i = 0; i < modeOptions.size(); i++) {
            var option = modeOptions.get(i);
            labels.append("this.modeList[")
                    .append(i)
                    .append("].text = $.i18n.prop(\"")
                    .append(escapeJsString(option.rawName()))
                    .append("\");\n");
        }

        return """
                (function () {
                    window.MinerConfig = {
                        modeList: [%s]
                    };
                    this.modeList = window.MinerConfig.modeList;
                    %s
                }).call(window);
                """.formatted(modes, labels);
    }

    private static String jsValue(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.matches("-?\\d+")) {
            return safe;
        }
        return "\"" + escapeJsString(safe) + "\"";
    }

    private static String escapeJsString(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
