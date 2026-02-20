package org.nomium.simulator.service;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.nomium.simulator.config.SimProperties;
import org.springframework.stereotype.Service;

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

    public record Snapshot(Pool p1, Pool p2, Pool p3, String workMode) {}

    final Object lock = new Object();

    Pool p1;
    Pool p2;
    Pool p3;

    // low | normal | high
    String workMode;

    public AntminerStateService(SimProperties props) {
        this.p1 = new Pool(props.getPoolUrl(), "worker1", "x");
        this.p2 = new Pool(props.getPoolUrl(), "worker2", "x");
        this.p3 = new Pool(props.getPoolUrl(), "worker3", "x");
        this.workMode = "normal";
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
        if (form == null || form.isEmpty()) return;

        synchronized (lock) {
            // обновляем только то, что реально пришло в форме
            p1 = mergePool(p1, 1, form);
            p2 = mergePool(p2, 2, form);
            p3 = mergePool(p3, 3, form);

            String mode = firstNonNull(
                    form.get("_ant_work_mode"),
                    form.get("bitmain-work-mode")
            );
            if (mode != null) {
                workMode = normalizeMode(mode);
            }
        }
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

    private static String normalizeMode(String v) {
        String m = (v == null ? "" : v.trim().toLowerCase());
        return switch (m) {
            case "low", "normal", "high" -> m;
            default -> "normal";
        };
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
