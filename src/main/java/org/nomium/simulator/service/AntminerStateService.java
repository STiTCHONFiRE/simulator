package org.nomium.simulator.service;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.nomium.simulator.config.SimProperties;
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

    public record ModeOption(String value, String name) {
    }

    private static final List<ModeOption> DEFAULT_MODE_OPTIONS = List.of(
            new ModeOption("0", "Normal"),
            new ModeOption("1", "Sleep"),
            new ModeOption("3", "High")
    );

    final Object lock = new Object();

    Pool p1;
    Pool p2;
    Pool p3;
    List<ModeOption> modeOptions;

    // The exposed raw values are configurable; the option label decides whether a value behaves as sleep, normal, or high.
    String workMode;

    public AntminerStateService(SimProperties props) {
        this.p1 = new Pool(props.getPoolUrl(), "worker1", "x");
        this.p2 = new Pool(props.getPoolUrl(), "worker2", "x");
        this.p3 = new Pool(props.getPoolUrl(), "worker3", "x");
        this.modeOptions = parseModeOptions(props.getModeOptions());
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

    public List<ModeOption> modeOptions() {
        return modeOptions;
    }

    public String modeName(String value) {
        String normalized = normalizeMode(value);
        return modeOptions.stream()
                .filter(option -> option.value().equalsIgnoreCase(normalized))
                .map(ModeOption::name)
                .findFirst()
                .orElseGet(() -> defaultModeOption().name());
    }

    public String modeKind(String value) {
        String raw = normalizeToken(value);
        for (ModeOption option : modeOptions) {
            if (option.value().equalsIgnoreCase(raw) || option.name().equalsIgnoreCase(raw)) {
                String kind = modeKind(option);
                return kind == null ? "normal" : kind;
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

    private static boolean hasPool(Pool pool) {
        return pool != null && pool.url != null && !pool.url.isBlank();
    }

    private String normalizeMode(String value) {
        String raw = normalizeToken(value);
        if (raw.isEmpty()) {
            return defaultModeOption().value();
        }

        for (ModeOption option : modeOptions) {
            if (option.value().equalsIgnoreCase(raw) || option.name().equalsIgnoreCase(raw)) {
                return option.value();
            }
        }

        String kind = knownModeKind(raw);
        if (kind != null) {
            for (ModeOption option : modeOptions) {
                if (kind.equals(modeKind(option))) {
                    return option.value();
                }
            }
        }

        return defaultModeOption().value();
    }

    private ModeOption defaultModeOption() {
        return modeOptions.stream()
                .filter(option -> "normal".equals(modeKind(option)))
                .findFirst()
                .orElse(modeOptions.getFirst());
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
            return new ModeOption(value, name);
        }

        String kind = knownModeKind(raw);
        return new ModeOption(defaultValueForKind(kind, raw), displayName(raw));
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
            case "high" -> "3";
            default -> fallback;
        };
    }

    private static String modeKind(ModeOption option) {
        String fromName = knownModeKind(option.name());
        if (fromName != null) {
            return fromName;
        }
        return knownModeKind(option.value());
    }

    private static String knownModeKind(String value) {
        String m = normalizeToken(value);
        return switch (m) {
            case "1", "254", "sleep", "standby", "low", "eco", "econom", "lpm", "modesleep", "mode_sleep" -> "sleep";
            case "0", "normal", "standard", "balance", "balanced", "normalt2", "modenormal", "mode_normal" -> "normal";
            case "2", "3", "high", "turbo", "performance", "boost", "hem", "modehem", "mode_high", "modehigh" -> "high";
            default -> null;
        };
    }

    private static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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
