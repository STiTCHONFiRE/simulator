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
            new ModeOption("0", "Sleep"),
            new ModeOption("1", "Normal"),
            new ModeOption("2", "High")
    );

    final Object lock = new Object();

    Pool p1;
    Pool p2;
    Pool p3;
    List<ModeOption> modeOptions;

    // Raw Antminer values usually are 0=sleep, 1=normal, 2=high, but the exposed list is configurable.
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
        String direct = canonicalModeValue(raw);
        if (direct != null) {
            return direct;
        }

        for (ModeOption option : modeOptions) {
            if (option.value().equalsIgnoreCase(raw)) {
                String fromName = canonicalModeValue(option.name());
                return fromName == null ? "1" : fromName;
            }
        }

        return "1";
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

        String canonical = canonicalModeValue(raw);
        if (canonical != null) {
            for (ModeOption option : modeOptions) {
                if (canonical.equals(canonicalModeValue(option.value())) ||
                        canonical.equals(canonicalModeValue(option.name()))) {
                    return option.value();
                }
            }
        }

        return defaultModeOption().value();
    }

    private ModeOption defaultModeOption() {
        return modeOptions.stream()
                .filter(option -> "1".equals(canonicalModeValue(option.value())) ||
                        "1".equals(canonicalModeValue(option.name())))
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

        String canonical = canonicalModeValue(raw);
        return new ModeOption(canonical == null ? raw : canonical, displayName(raw));
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
        String canonical = canonicalModeValue(raw);
        if ("0".equals(canonical)) {
            return "Sleep";
        }
        if ("1".equals(canonical)) {
            return "Normal";
        }
        if ("2".equals(canonical)) {
            return "High";
        }

        String value = raw.trim();
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static String canonicalModeValue(String value) {
        String m = normalizeToken(value);
        return switch (m) {
            case "0", "254", "sleep", "standby", "low", "eco", "econom", "lpm", "modesleep", "mode_sleep" -> "0";
            case "1", "normal", "standard", "balance", "balanced", "normalt2", "modenormal", "mode_normal" -> "1";
            case "2", "3", "high", "turbo", "performance", "boost", "hem", "modehem", "mode_high", "modehigh" -> "2";
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
