package org.nomium.sumulator.tcp;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.nomium.sumulator.config.SimProperties;
import org.nomium.sumulator.service.TelemetryService;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CgminerTcpServer implements SmartLifecycle {

    SimProperties props;
    TelemetryService telemetry;
    ObjectMapper objectMapper;

    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    List<ServerSocket> sockets = new ArrayList<>();

    @NonFinal
    volatile boolean running = false;

    @Override
    public void start() {
        if (running) return;
        running = true;

        log.info("Starting TCP Server, {}", props.getPoolUrl());

        for (int port : parsePorts(props.getCgminer().getPortsCsv())) {
            try {
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress("0.0.0.0", port));
                sockets.add(ss);

                executor.submit(() -> acceptLoop(ss));
                log.info("[asic-sim] cgminer TCP listening on :{}", port);
            } catch (Exception e) {
                log.error("[asic-sim] failed to bind cgminer port {}: {}", port, e.getMessage());
            }
        }
    }

    private void acceptLoop(ServerSocket ss) {
        while (running && !ss.isClosed()) {
            try {
                Socket s = ss.accept();
                executor.submit(() -> handle(s));
            } catch (Exception e) {
                if (running) {
                    // на shutdown тут часто летит SocketException - не шуметь
                }
            }
        }
    }

    private void handle(Socket s) {
        try (s) {
            s.setSoTimeout(props.getCgminer().getSocketReadTimeoutMs());

            String clientIp = ((InetSocketAddress) s.getRemoteSocketAddress()).getAddress().getHostAddress();

            byte[] buf = new byte[4096];
            int n;
            try {
                InputStream in = s.getInputStream();
                n = in.read(buf);
            } catch (SocketTimeoutException timeout) {
                // порт-проба: connect без data
                return;
            }

            String text = (n > 0)
                    ? new String(buf, 0, n, StandardCharsets.UTF_8).trim().toLowerCase()
                    : "";

            Map<String, Object> payload;
            if (text.contains("stats")) {
                payload = telemetry.cgminerStats(clientIp);
            } else if (text.contains("devdetails") || text.contains("devs")) {
                payload = telemetry.cgminerDevDetails(clientIp);
            } else {
                payload = telemetry.cgminerSummary(clientIp);
            }

            byte[] out = objectMapper.writeValueAsBytes(payload);
            OutputStream os = s.getOutputStream();
            os.write(out);
            os.write('\n');
            os.flush();
        } catch (Exception ignored) {
        }
    }

    private static List<Integer> parsePorts(String csv) {
        String v = (csv == null || csv.isBlank()) ? "4028,4029" : csv;
        String[] parts = v.split(",");
        List<Integer> ports = new ArrayList<>();
        for (String p : parts) {
            try {
                ports.add(Integer.parseInt(p.trim()));
            } catch (Exception ignored) {
            }
        }
        if (ports.isEmpty()) ports.add(4028);
        return ports;
    }

    @Override
    public void stop() {
        running = false;
        for (ServerSocket ss : sockets) {
            try { ss.close(); } catch (Exception ignored) {}
        }
        sockets.clear();
        executor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

}
