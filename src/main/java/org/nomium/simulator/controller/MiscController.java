package org.nomium.simulator.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.nomium.simulator.config.SimProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MiscController {

    SimProperties props;

    @GetMapping(value = {"/", "/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public String index() {
        return "<html><body><h1>" + props.getVendor() + " " + props.getModel() + "</h1>"
                + "<p>Firmware: " + props.getFirmware() + "</p></body></html>";
    }
}
