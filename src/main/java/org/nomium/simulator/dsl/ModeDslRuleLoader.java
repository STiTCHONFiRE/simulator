package org.nomium.simulator.dsl;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class ModeDslRuleLoader {

    private final ObjectMapper objectMapper;
    private final PathMatchingResourcePatternResolver resourceResolver;

    public ModeDslRuleLoader() {
        this(
                JsonMapper.builder()
                        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                        .build(),
                new PathMatchingResourcePatternResolver()
        );
    }

    ModeDslRuleLoader(ObjectMapper objectMapper, PathMatchingResourcePatternResolver resourceResolver) {
        this.objectMapper = objectMapper;
        this.resourceResolver = resourceResolver;
    }

    public ModeDslRule load(String rulesPath, String ruleKey) {
        List<ModeDslRule> rules = loadAll(rulesPath);
        String normalizedKey = normalize(ruleKey);

        if (normalizedKey == null) {
            if (rules.size() == 1) {
                return rules.getFirst();
            }
            throw new IllegalStateException(
                    "Mode DSL rule key must be configured when rules path contains " + rules.size() + " rules"
            );
        }

        return rules.stream()
                .filter(rule -> rule.key().equalsIgnoreCase(normalizedKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Mode DSL rule '" + normalizedKey + "' was not found in '" + rulesPath + "'"
                ));
    }

    public List<ModeDslRule> loadAll(String rulesPath) {
        String normalizedPath = normalize(rulesPath);
        if (normalizedPath == null) {
            throw new IllegalStateException("Mode DSL rules path must be configured");
        }

        try {
            List<Resource> resources = resolveResources(normalizedPath);
            if (resources.isEmpty()) {
                throw new IllegalStateException("No Mode DSL JSON files were found in '" + normalizedPath + "'");
            }

            List<ModeDslRule> rules = new ArrayList<>(resources.size());
            for (Resource resource : resources) {
                rules.add(parse(resource));
            }

            rules.sort(Comparator.comparingInt(ModeDslRule::priority).reversed()
                    .thenComparing(ModeDslRule::key, String.CASE_INSENSITIVE_ORDER));
            validateUniqueRules(rules);
            return List.copyOf(rules);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Mode DSL rules from '" + normalizedPath + "'", e);
        }
    }

    private List<Resource> resolveResources(String rulesPath) throws IOException {
        if (rulesPath.startsWith("classpath:")) {
            String classpathPath = rulesPath.substring("classpath:".length()).replace('\\', '/');
            while (classpathPath.startsWith("/")) {
                classpathPath = classpathPath.substring(1);
            }

            String pattern = classpathPath.toLowerCase(Locale.ROOT).endsWith(".json")
                    ? "classpath:" + classpathPath
                    : "classpath*:" + stripTrailingSlash(classpathPath) + "/**/*.json";

            return Arrays.stream(resourceResolver.getResources(pattern))
                    .filter(Resource::exists)
                    .sorted(Comparator.comparing(Resource::getDescription, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }

        Path path = rulesPath.startsWith("file:")
                ? Path.of(URI.create(rulesPath))
                : Path.of(rulesPath);

        if (Files.isRegularFile(path)) {
            return List.of(new FileSystemResource(path));
        }
        if (!Files.isDirectory(path)) {
            return List.of();
        }

        try (Stream<Path> files = Files.walk(path)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                    .map(FileSystemResource::new)
                    .map(Resource.class::cast)
                    .toList();
        }
    }

    private ModeDslRule parse(Resource resource) throws IOException {
        JsonNode root;
        try (var input = resource.getInputStream()) {
            root = objectMapper.readTree(input);
        }

        if (root == null || !root.isObject()) {
            throw invalid(resource, "root must be a JSON object");
        }

        String key = requiredText(root.get("key"), resource, "key");
        int priority = root.path("priority").asInt(0);
        JsonNode optionsNode = root.path("when").path("allOptionsExact");
        if (!optionsNode.isArray() || optionsNode.isEmpty()) {
            throw invalid(resource, "when.allOptionsExact must contain at least one option");
        }

        List<ModeDslRule.Option> options = new ArrayList<>();
        Set<String> optionNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> rawValues = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (JsonNode optionNode : optionsNode) {
            String name = requiredText(optionNode.get("name"), resource, "when.allOptionsExact[].name");
            String value = requiredText(optionNode.get("value"), resource, "when.allOptionsExact[].value");
            if (!optionNames.add(name)) {
                throw invalid(resource, "duplicate option name '" + name + "'");
            }
            options.add(new ModeDslRule.Option(name, value));
            rawValues.add(value);
        }

        JsonNode supportedModesNode = root.path("then").path("supportedModes");
        if (!supportedModesNode.isObject() || supportedModesNode.isEmpty()) {
            throw invalid(resource, "then.supportedModes must contain at least one mapping");
        }

        Map<String, String> supportedModes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> mapping : supportedModesNode.properties()) {
            String mode = normalize(mapping.getKey());
            String rawValue = requiredText(mapping.getValue(), resource, "then.supportedModes." + mapping.getKey());
            if (mode == null) {
                throw invalid(resource, "then.supportedModes contains an empty mode name");
            }
            if (!rawValues.contains(rawValue)) {
                throw invalid(resource, "mode '" + mode + "' points to unknown raw value '" + rawValue + "'");
            }
            supportedModes.put(mode, rawValue);
        }

        return new ModeDslRule(key, priority, List.copyOf(options), Map.copyOf(supportedModes));
    }

    private static void validateUniqueRules(List<ModeDslRule> rules) {
        Set<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> signatures = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (ModeDslRule rule : rules) {
            if (!keys.add(rule.key())) {
                throw new IllegalStateException("Mode DSL contains duplicate rule key '" + rule.key() + "'");
            }

            String signature = rule.options().stream()
                    .map(option -> option.rawName().trim() + "=" + option.rawValue().trim())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .reduce((left, right) -> left + "|" + right)
                    .orElse("");
            if (!signatures.add(signature)) {
                throw new IllegalStateException("Mode DSL contains duplicate allOptionsExact matcher '" + signature + "'");
            }
        }
    }

    private static String requiredText(JsonNode node, Resource resource, String property) {
        String value = node == null || node.isNull() ? null : normalize(node.asString());
        if (value == null) {
            throw invalid(resource, "property '" + property + "' must be a non-empty scalar");
        }
        return value;
    }

    private static IllegalStateException invalid(Resource resource, String reason) {
        return new IllegalStateException("Invalid Mode DSL rule '" + resource.getDescription() + "': " + reason);
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
