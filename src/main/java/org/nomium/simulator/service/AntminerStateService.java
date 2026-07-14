package org.nomium.simulator.service;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.nomium.simulator.config.SimProperties;
import org.nomium.simulator.dsl.ModeDslRule;
import org.nomium.simulator.dsl.ModeDslRuleLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AntminerStateService {

    @Getter
    public static final class Pool {
        String url;
        String user;
        String pw;

        Pool(String url, String user, String pw) {
            this.url = url == null ? "" : url;
            this.user = user == null ? "" : user;
            this.pw = pw == null ? "" : pw;
        }
    }

    public record Snapshot(Pool p1, Pool p2, Pool p3, String workMode) {
        public boolean hasPools() {
            return hasPool(p1) || hasPool(p2) || hasPool(p3);
        }
    }

    public record ModeOption(String value, String rawName, String kind) {
    }

    private static final List<ModeOption> DEFAULT_MODE_OPTIONS = List.of(
            new ModeOption("0", "modeNormal", "normal"),
            new ModeOption("1", "modeSleep", "sleep"),
            new ModeOption("3", "modeHEM", "high")
    );

    final Object lock = new Object();

    Pool p1;
    Pool p2;
    Pool p3;
    final List<ModeOption> modeOptions;

    // The exposed raw values and names must remain exact: server-side DSL matching compares the complete pair set.
    String workMode;
    boolean blink;
    final String modeDslRuleKey;

    public AntminerStateService(SimProperties props) {
        this.p1 = new Pool(props.getPoolUrl(), "worker1", "x");
        this.p2 = new Pool(props.getPoolUrl(), "worker2", "x");
        this.p3 = new Pool(props.getPoolUrl(), "worker3", "x");
        String configuredRuleKey = normalizeNullable(props.getModeDsl().getRuleKey());
        if (configuredRuleKey == null) {
            this.modeOptions = parseModeOptions(props.getModeOptions());
            this.modeDslRuleKey = null;
        } else {
            ModeDslRule rule = new ModeDslRuleLoader().load(props.getModeDsl().getRulesPath(), configuredRuleKey);
            this.modeOptions = modeOptionsFrom(rule);
            this.modeDslRuleKey = rule.key();
        }
        this.workMode = normalizeMode(props.getDefaultWorkMode());
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            return new Snapshot(
                    new Pool(p1.url, p1.user, p1.pw),
                    new Pool(p2.url, p2.user, p2.pw),
                    new Pool(p3.url, p3.user, p3.pw),
                    workMode
            );
        }
    }

    public void applyForm(Map<String, String> form) {
        if (form == null || form.isEmpty()) {
            return;
        }

        synchronized (lock) {
            p1 = mergePool(p1, 1, form);
            p2 = mergePool(p2, 2, form);
            p3 = mergePool(p3, 3, form);

            String mode = firstNonNull(
                    form.get("_ant_work_mode"),
                    form.get("bitmain-work-mode"),
                    form.get("WorkModeValue"),
                    form.get("work_mode"),
                    form.get("work-mode"),
                    form.get("miner-mode"),
                    form.get("mode")
            );
            if (mode != null) {
                workMode = normalizeMode(mode);
            }
        }
    }

    public void applyJson(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return;
        }

        synchronized (lock) {
            Object pools = getIgnoreCase(config, "pools");
            if (pools instanceof List<?> poolList) {
                p1 = jsonPool(poolList, 0, p1);
                p2 = jsonPool(poolList, 1, p2);
                p3 = jsonPool(poolList, 2, p3);
            }

            String mode = firstNonNull(
                    scalar(config, "_ant_work_mode"),
                    scalar(config, "bitmain-work-mode"),
                    scalar(config, "WorkModeValue"),
                    scalar(config, "work_mode"),
                    scalar(config, "work-mode"),
                    scalar(config, "miner-mode"),
                    scalar(config, "mode")
            );
            if (mode != null) {
                workMode = normalizeMode(mode);
            }
        }
    }

    public void setBlink(boolean enabled) {
        synchronized (lock) {
            blink = enabled;
        }
    }

    public boolean blink() {
        synchronized (lock) {
            return blink;
        }
    }

    public String modeDslRuleKey() {
        return modeDslRuleKey;
    }

    public List<ModeOption> modeOptions() {
        return modeOptions;
    }

    public String modeName(String value) {
        String normalized = normalizeMode(value);
        return modeOptions.stream()
                .filter(option -> option.value().equalsIgnoreCase(normalized))
                .map(ModeOption::rawName)
                .findFirst()
                .orElseGet(() -> defaultModeOption().rawName());
    }

    public String modeKind(String value) {
        String raw = normalizeToken(value);
        for (ModeOption option : modeOptions) {
            if (option.value().equalsIgnoreCase(raw) || option.rawName().equalsIgnoreCase(raw)) {
                return option.kind();
            }
        }

        String direct = knownModeKind(raw);
        return direct == null ? "normal" : direct;
    }

    private static Pool mergePool(Pool old, int n, Map<String, String> form) {
        String urlKey = "_ant_pool" + n + "url";
        String userKey = "_ant_pool" + n + "user";
        String pwKey = "_ant_pool" + n + "pw";

        String url = form.containsKey(urlKey) ? form.get(urlKey) : old.url;
        String user = form.containsKey(userKey) ? form.get(userKey) : old.user;
        String pw = form.containsKey(pwKey) ? form.get(pwKey) : old.pw;

        return new Pool(url, user, pw);
    }

    private static Pool jsonPool(List<?> pools, int index, Pool old) {
        if (index >= pools.size() || !(pools.get(index) instanceof Map<?, ?> rawPool)) {
            return old;
        }

        String url = scalar(rawPool, "url");
        String user = scalar(rawPool, "user");
        String password = firstNonNull(scalar(rawPool, "pass"), scalar(rawPool, "password"));
        return new Pool(
                url == null ? old.url : url,
                user == null ? old.user : user,
                password == null ? old.pw : password
        );
    }

    private static boolean hasPool(Pool pool) {
        return pool != null && pool.url != null && !pool.url.isBlank();
    }

    private String normalizeMode(String value) {
        String raw = normalizeToken(value);
        if (raw.isEmpty()) {
            return defaultModeOption().value();
        }

        for (ModeOption option : modeOptions) {
            if (option.value().equalsIgnoreCase(raw) || option.rawName().equalsIgnoreCase(raw)) {
                return option.value();
            }
        }

        String kind = knownModeKind(raw);
        if (kind != null) {
            for (ModeOption option : modeOptions) {
                if (kind.equals(option.kind())) {
                    return option.value();
                }
            }
        }

        return defaultModeOption().value();
    }

    private ModeOption defaultModeOption() {
        return modeOptions.stream()
                .filter(option -> "normal".equals(option.kind()))
                .findFirst()
                .orElse(modeOptions.getFirst());
    }

    private static List<ModeOption> modeOptionsFrom(ModeDslRule rule) {
        List<ModeOption> result = rule.options().stream()
                .map(option -> {
                    String kind = rule.semanticKind(option.rawValue());
                    if (kind == null) {
                        kind = knownModeKind(option.rawName());
                    }
                    return new ModeOption(
                            option.rawValue(),
                            option.rawName(),
                            kind == null ? "normal" : kind
                    );
                })
                .toList();

        if (result.isEmpty()) {
            throw new IllegalStateException("Mode DSL rule '" + rule.key() + "' contains no options");
        }
        return result;
    }

    private static List<ModeOption> parseModeOptions(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MODE_OPTIONS;
        }

        List<ModeOption> result = new ArrayList<>();
        for (String token : configured.split("[,;]")) {
            ModeOption option = parseModeOption(token);
            if (option == null || containsValue(result, option.value())) {
                continue;
            }
            result.add(option);
        }

        return result.isEmpty() ? DEFAULT_MODE_OPTIONS : List.copyOf(result);
    }

    private static ModeOption parseModeOption(String token) {
        String raw = token == null ? "" : token.trim();
        if (raw.isEmpty()) {
            return null;
        }

        int separator = separatorIndex(raw);
        if (separator >= 0) {
            String value = raw.substring(0, separator).trim();
            String name = raw.substring(separator + 1).trim();
            if (value.isEmpty() || name.isEmpty()) {
                return null;
            }
            String kind = knownModeKind(name);
            if (kind == null) {
                kind = knownModeKind(value);
            }
            return new ModeOption(value, rawOptionName(name), kind == null ? "normal" : kind);
        }

        String kind = knownModeKind(raw);
        String name = displayName(raw);
        return new ModeOption(
                defaultValueForKind(kind, raw),
                rawOptionName(name),
                kind == null ? "normal" : kind
        );
    }

    private static int separatorIndex(String raw) {
        int colon = raw.indexOf(':');
        int equals = raw.indexOf('=');
        if (colon < 0) {
            return equals;
        }
        if (equals < 0) {
            return colon;
        }
        return Math.min(colon, equals);
    }

    private static boolean containsValue(List<ModeOption> options, String value) {
        return options.stream().anyMatch(option -> option.value().equalsIgnoreCase(value));
    }

    private static String displayName(String raw) {
        String kind = knownModeKind(raw);
        if ("sleep".equals(kind)) {
            return "Sleep";
        }
        if ("normal".equals(kind)) {
            return "Normal";
        }
        if ("high".equals(kind)) {
            return "High";
        }
        if ("low".equals(kind)) {
            return "Low";
        }

        String value = raw.trim();
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static String defaultValueForKind(String kind, String fallback) {
        return switch (kind == null ? "" : kind) {
            case "normal" -> "0";
            case "sleep" -> "1";
            case "low" -> "2";
            case "high" -> "3";
            default -> fallback;
        };
    }

    private static String rawOptionName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.regionMatches(true, 0, "mode", 0, 4)) {
            return normalized;
        }

        return switch (normalizeToken(normalized)) {
            case "normal" -> "modeNormal";
            case "sleep" -> "modeSleep";
            case "low", "lowpower" -> "modeLowPower";
            case "high" -> "modeHEM";
            default -> normalized;
        };
    }

    private static String knownModeKind(String value) {
        String m = normalizeToken(value);
        return switch (m) {
            case "1", "254", "sleep", "standby", "modesleep", "mode_sleep" -> "sleep";
            case "0", "normal", "standard", "balance", "balanced", "normalt2", "modenormal", "mode_normal" -> "normal";
            case "low", "lowpower", "eco", "econom", "lpm", "modelowpower", "mode_low", "modelow" -> "low";
            case "2", "3", "high", "turbo", "performance", "boost", "hem", "modehem", "mode_high", "modehigh" -> "high";
            default -> null;
        };
    }

    private static String scalar(Map<?, ?> values, String name) {
        Object value = getIgnoreCase(values, name);
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
            return null;
        }
        return String.valueOf(value);
    }

    private static Object getIgnoreCase(Map<?, ?> values, String name) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (entry.getKey() != null && name.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonNull(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
