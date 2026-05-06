package org.nomium.simulator.service;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.nomium.simulator.config.SimProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FirmwareSimulationService {

    SimProperties props;
    AtomicReference<State> state;

    public FirmwareSimulationService(SimProperties props) {
        this.props = props;
        this.state = new AtomicReference<>(State.ready(props.getFirmware(), props.getSystemFilesystemVersion()));
    }

    public Map<String, Object> acceptUpload(MultipartFile datafile) {
        if (datafile == null || datafile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Firmware datafile is required");
        }

        String fileName = cleanFileName(datafile.getOriginalFilename());
        String targetFirmware = inferFirmwareVersion(fileName);
        State next = State.uploaded(
                currentFirmware(),
                currentFilesystemVersion(),
                fileName,
                datafile.getSize(),
                targetFirmware
        );
        state.set(next);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stats", "success");
        result.put("msg", "Firmware upload accepted. Rebooting System");
        result.put("success", true);
        result.put("status", next.status());
        result.put("file", next.uploadedFileName());
        result.put("sizeBytes", next.uploadedSizeBytes());
        result.put("targetFirmware", next.pendingFirmware());
        return result;
    }

    public void markRebootRequested(long availableAtMs) {
        state.updateAndGet(current -> {
            if (!StringUtils.hasText(current.pendingFirmware())) {
                return current;
            }

            return current.withStatus("installing", availableAtMs);
        });
    }

    public void completeIfReady() {
        State current = state.get();
        if (!"installing".equals(current.status()) || System.currentTimeMillis() < current.applyAtMs()) {
            return;
        }

        State installed = current.installed();
        if (state.compareAndSet(current, installed)) {
            props.setFirmware(installed.currentFirmware());
            props.setSystemFilesystemVersion(installed.currentFilesystemVersion());
        }
    }

    public Map<String, Object> status() {
        completeIfReady();

        State current = state.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("status", current.status());
        result.put("currentFirmware", current.currentFirmware());
        result.put("currentFilesystemVersion", current.currentFilesystemVersion());
        result.put("pendingFirmware", current.pendingFirmware());
        result.put("uploadedFileName", current.uploadedFileName());
        result.put("uploadedSizeBytes", current.uploadedSizeBytes());
        result.put("applyAt", current.applyAtMs() <= 0 ? null : Instant.ofEpochMilli(current.applyAtMs()).toString());
        return result;
    }

    private String currentFirmware() {
        completeIfReady();
        return props.getFirmware();
    }

    private String currentFilesystemVersion() {
        completeIfReady();
        return props.getSystemFilesystemVersion();
    }

    private static String cleanFileName(String originalFileName) {
        String value = originalFileName == null ? "firmware" : StringUtils.cleanPath(originalFileName);
        if (!StringUtils.hasText(value) || ".".equals(value) || "..".equals(value)) {
            return "firmware";
        }
        return value;
    }

    private static String inferFirmwareVersion(String fileName) {
        String value = fileName;
        for (String suffix : new String[]{".tar.gz", ".tgz", ".tar", ".gz", ".bin", ".swu", ".img", ".zip"}) {
            if (value.toLowerCase().endsWith(suffix)) {
                value = value.substring(0, value.length() - suffix.length());
                break;
            }
        }

        return StringUtils.hasText(value) ? value : "simulated-firmware";
    }

    private record State(
            String status,
            String currentFirmware,
            String currentFilesystemVersion,
            String uploadedFileName,
            long uploadedSizeBytes,
            String pendingFirmware,
            long applyAtMs
    ) {

        static State ready(String firmware, String filesystemVersion) {
            return new State("ready", firmware, filesystemVersion, null, 0, null, 0);
        }

        static State uploaded(
                String firmware,
                String filesystemVersion,
                String uploadedFileName,
                long uploadedSizeBytes,
                String pendingFirmware
        ) {
            return new State("uploaded", firmware, filesystemVersion, uploadedFileName, uploadedSizeBytes, pendingFirmware, 0);
        }

        State withStatus(String status, long applyAtMs) {
            return new State(status, currentFirmware, currentFilesystemVersion, uploadedFileName, uploadedSizeBytes, pendingFirmware, applyAtMs);
        }

        State installed() {
            return new State("installed", pendingFirmware, pendingFirmware, uploadedFileName, uploadedSizeBytes, null, 0);
        }
    }
}
