package org.nomium.simulator.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.nomium.simulator.config.SimProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RebootService {

    SimProperties props;
    TelemetryService telemetry;

    AtomicLong rebootUntilMs = new AtomicLong(0);

    public void requestReboot() {
        long now = System.currentTimeMillis();
        long until = now + Math.max(1000, props.getRebootDowntime().toMillis());
        rebootUntilMs.set(until);

        // uptime должен "обнулиться" когда устройство снова станет доступно:
        telemetry.setStartedAt(Instant.ofEpochMilli(until));
    }

    public boolean isRebooting() {
        return System.currentTimeMillis() < rebootUntilMs.get();
    }

    public long remainingMs() {
        long r = rebootUntilMs.get() - System.currentTimeMillis();
        return Math.max(0, r);
    }
}
