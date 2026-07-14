package org.nomium.simulator.dsl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nomium.simulator.config.SimProperties;
import org.nomium.simulator.service.AntminerStateService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModeDslRuleLoaderTest {

    private final ModeDslRuleLoader loader = new ModeDslRuleLoader();

    @TempDir
    Path tempDirectory;

    @Test
    void loadsAllBundledAntminerRulesUsingBackendDslSchema() {
        List<ModeDslRule> rules = loader.loadAll("classpath:mode-dsl");

        assertEquals(8, rules.size());
        assertEquals(
                List.of(
                        "bitmain.hem2",
                        "bitmain.hem2-dry5",
                        "bitmain.hem3",
                        "bitmain.low-power2",
                        "bitmain.low-power3",
                        "bitmain.normal-sleep-only",
                        "bitmain.reduced-hem2",
                        "bitmain.reduced-low-power3"
                ),
                rules.stream().map(ModeDslRule::key).sorted().toList()
        );
    }

    @Test
    void selectedRulePreservesExactOptionPairsAndSemanticModes() {
        ModeDslRule rule = loader.load("classpath:mode-dsl", "bitmain.low-power3");

        assertEquals(
                List.of(
                        new ModeDslRule.Option("modeNormal", "0"),
                        new ModeDslRule.Option("modeSleep", "1"),
                        new ModeDslRule.Option("modeLowPower", "3")
                ),
                rule.options()
        );
        assertEquals("normal", rule.semanticKind("0"));
        assertEquals("sleep", rule.semanticKind("1"));
        assertEquals("low", rule.semanticKind("3"));
    }

    @Test
    void antminerStateUsesDslPairsInsteadOfLegacyModeOptions() {
        SimProperties props = new SimProperties();
        props.setModeOptions("0:Normal,1:Sleep,3:High");
        props.getModeDsl().setRuleKey("bitmain.reduced-low-power3");
        props.setDefaultWorkMode("low");

        AntminerStateService state = new AntminerStateService(props);

        assertEquals("bitmain.reduced-low-power3", state.modeDslRuleKey());
        assertEquals(
                List.of(
                        new AntminerStateService.ModeOption("0", "modeNormal", "normal"),
                        new AntminerStateService.ModeOption("3", "modeLowPower", "low")
                ),
                state.modeOptions()
        );
        assertEquals("3", state.snapshot().workMode());
        assertEquals("low", state.modeKind("3"));
    }

    @Test
    void acceptsCommentsAndTrailingCommasLikeBackendDslLoader() throws IOException {
        Path ruleFile = tempDirectory.resolve("custom.json");
        Files.writeString(ruleFile, """
                {
                  // This syntax is accepted by EquipmentModeDslRuleLoader as well.
                  "key": "bitmain.custom",
                  "priority": 10,
                  "when": {
                    "allOptionsExact": [
                      { "name": "modeNormal", "value": "0" },
                      { "name": "modeSleep", "value": "1" },
                    ]
                  },
                  "then": {
                    "supportedModes": {
                      "Normal": "0",
                      "Sleep": "1",
                    }
                  }
                }
                """);

        ModeDslRule rule = loader.load(ruleFile.toString(), null);

        assertEquals("bitmain.custom", rule.key());
        assertEquals(2, rule.options().size());
    }
}
