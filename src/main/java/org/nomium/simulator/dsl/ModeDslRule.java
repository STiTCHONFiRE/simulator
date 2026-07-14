package org.nomium.simulator.dsl;

import java.util.List;
import java.util.Map;

public record ModeDslRule(
        String key,
        int priority,
        List<Option> options,
        Map<String, String> supportedModes
) {
    public record Option(String rawName, String rawValue) {
    }

    public String semanticKind(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        return supportedModes.entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(rawValue.trim()))
                .map(entry -> entry.getKey().trim().toLowerCase())
                .findFirst()
                .orElse(null);
    }
}
