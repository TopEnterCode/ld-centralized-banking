package com.example.banking.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class LaunchDarklyBootstrap {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_BASE = "https://app.launchdarkly.com/api/v2";
    private static final String API_VERSION = "20240415";
    private static final String SEMANTIC_PATCH =
            "application/json; domain-model=launchdarkly.semanticpatch";
    private static final Pattern KEBAB_CASE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final List<SegmentSpec> SEGMENTS =
            List.of(
                    new SegmentSpec(
                            "bank-employees",
                            "Bank Employees",
                            "Synthetic employee contexts used by the banking POC",
                            "employee",
                            true),
                    new SegmentSpec(
                            "pilot-customers",
                            "Pilot Customers",
                            "Synthetic pilot cohort used by the banking POC",
                            "cohort",
                            "pilot"));
    private static final List<FlagSpec> FLAGS =
            List.of(
                    boolFlag(
                            "client-new-payment-ui",
                            "Client New Payment UI",
                            "Switches the browser between the legacy and new synthetic payment UI.",
                            "Legacy UI",
                            "New UI",
                            true,
                            true,
                            List.of("mali-pilot"),
                            List.of("bank-employees", "pilot-customers")),
                    boolFlag(
                            "profile-response-v2",
                            "Profile Response V2",
                            "Selects the legacy or v2 synthetic customer profile response.",
                            "Legacy profile",
                            "Profile v2",
                            false,
                            true,
                            List.of(),
                            List.of("pilot-customers")),
                    stringFlag(
                            "payment-api-migration",
                            "Payment API Migration",
                            "Controls the safe payment migration stage: off, shadow, live, or complete.",
                            List.of("off", "shadow", "live", "complete"),
                            "off",
                            "shadow",
                            List.of("pilot-customers")),
                    boolFlag(
                            "payment-v2-enabled",
                            "Payment V2 Enabled",
                            "Kill switch that must be enabled before payment v2 can be authoritative.",
                            "V1 only",
                            "V2 allowed",
                            false,
                            true,
                            List.of(),
                            List.of("pilot-customers")),
                    stringFlag(
                            "fraud-engine-version",
                            "Fraud Engine Version",
                            "Selects the v1 or v2 synthetic fraud decision engine.",
                            List.of("v1", "v2"),
                            "v1",
                            "v2",
                            List.of("pilot-customers")),
                    stringFlag(
                            "notification-provider",
                            "Notification Provider",
                            "Routes synthetic notifications to provider A or provider B.",
                            List.of("provider-a", "provider-b"),
                            "provider-a",
                            "provider-b",
                            List.of("pilot-customers")));

    private LaunchDarklyBootstrap() {}

    public static void main(String[] args) throws Exception {
        boolean apply = Arrays.asList(args).contains("--apply");
        boolean confirmed = argument(args, "--confirm").map("APPLY"::equals).orElse(false);
        Map<String, String> localSettings = loadDotEnv();
        String project = setting(localSettings, "LD_PROJECT_KEY", "centrailized-banking");
        String environment = setting(localSettings, "LD_ENVIRONMENT_KEY", "devolopment");

        requireKebabCase("project", project);
        requireKebabCase("environment", environment);
        rejectProduction(environment);
        printPlan(project, environment, apply);
        if (!apply) {
            System.out.println("Preview only. No external requests were made.");
            return;
        }
        if (!confirmed) {
            throw new IllegalArgumentException("Apply requires: --apply --confirm APPLY");
        }

        String token = requiredSetting(localSettings, "LD_API_ACCESS_TOKEN");
        requiredSetting(localSettings, "LD_SDK_KEY");
        requiredSetting(localSettings, "LD_CLIENT_SIDE_ID");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        validateDestination(client, token, project, environment);
        for (SegmentSpec segment : SEGMENTS) {
            ensureSegment(client, token, project, environment, segment);
        }
        for (FlagSpec flag : FLAGS) {
            ensureFlag(client, token, project, environment, flag);
        }
        System.out.println(
                "Apply completed. Required flags and segments are present; all flags remain off with safe fallbacks.");
    }

    private static void validateDestination(
            HttpClient client, String token, String project, String environment) throws Exception {
        requireStatus(
                request(client, token, "GET", apiPath("projects", project), null, null),
                200,
                "project validation");
        requireStatus(
                request(
                        client,
                        token,
                        "GET",
                        apiPath("projects", project, "environments", environment),
                        null,
                        null),
                200,
                "environment validation");
        System.out.printf("Validated non-production destination %s/%s%n", project, environment);
    }

    private static void ensureSegment(
            HttpClient client,
            String token,
            String project,
            String environment,
            SegmentSpec segment)
            throws Exception {
        String path = apiPath("segments", project, environment, segment.key());
        ApiResponse response = request(client, token, "GET", path, null, null);
        if (response.status() == 404) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("key", segment.key());
            payload.put("name", segment.name());
            payload.put("description", segment.description());
            payload.put("tags", List.of("banking-poc", "synthetic-data"));
            payload.put("unbounded", false);
            requireStatus(
                    request(
                            client,
                            token,
                            "POST",
                            apiPath("segments", project, environment),
                            payload,
                            "application/json"),
                    201,
                    "create segment " + segment.key());
            response = request(client, token, "GET", path, null, null);
        }
        requireStatus(response, 200, "read segment " + segment.key());
        JsonNode current = readBody(response, "segment " + segment.key());
        if (!hasSegmentRule(current, segment)) {
            Map<String, Object> instruction = new LinkedHashMap<>();
            instruction.put("kind", "addRule");
            instruction.put("description", segment.description());
            instruction.put("clauses", List.of(segment.clause()));
            requireStatus(
                    request(
                            client,
                            token,
                            "PATCH",
                            path,
                            Map.of("instructions", List.of(instruction)),
                            SEMANTIC_PATCH),
                    200,
                    "configure segment " + segment.key());
            System.out.printf("  segment %-24s created/configured%n", segment.key());
        } else {
            System.out.printf("  segment %-24s already configured%n", segment.key());
        }
    }

    private static void ensureFlag(
            HttpClient client, String token, String project, String environment, FlagSpec flag)
            throws Exception {
        String path = apiPath("flags", project, flag.key());
        ApiResponse response = request(client, token, "GET", path, null, null);
        boolean created = false;
        if (response.status() == 404) {
            response =
                    request(
                            client,
                            token,
                            "POST",
                            apiPath("flags", project),
                            flag.payload(),
                            "application/json");
            requireStatus(response, 201, "create flag " + flag.key());
            created = true;
        } else {
            requireStatus(response, 200, "read flag " + flag.key());
        }

        JsonNode current = readBody(response, "flag " + flag.key());
        validateFlagShape(current, flag);
        configureFlagTargeting(client, token, path, environment, current, flag);
        System.out.printf(
                "  flag    %-24s %s; targeting remains off%n",
                flag.key(), created ? "created/configured" : "already present/verified");
    }

    private static void configureFlagTargeting(
            HttpClient client,
            String token,
            String path,
            String environment,
            JsonNode flagNode,
            FlagSpec flag)
            throws Exception {
        String variationId = variationId(flagNode, flag.targetValue());
        int variationIndex = variationIndex(flagNode, flag.targetValue());
        JsonNode environmentNode = flagNode.path("environments").path(environment);
        List<Map<String, Object>> instructions = new ArrayList<>();

        List<String> missingTargets =
                flag.individualTargets().stream()
                        .filter(key -> !hasIndividualTarget(environmentNode, key, variationIndex))
                        .toList();
        if (!missingTargets.isEmpty()) {
            instructions.add(
                    Map.of(
                            "kind",
                            "addTargets",
                            "contextKind",
                            "user",
                            "variationId",
                            variationId,
                            "values",
                            missingTargets));
        }
        for (String segmentKey : flag.segmentTargets()) {
            if (!hasFlagSegmentRule(environmentNode, segmentKey, variationIndex)) {
                Map<String, Object> instruction = new LinkedHashMap<>();
                instruction.put("kind", "addRule");
                instruction.put("variationId", variationId);
                instruction.put("description", "Synthetic segment: " + segmentKey);
                instruction.put(
                        "clauses",
                        List.of(
                                Map.of(
                                        "contextKind",
                                        "user",
                                        "attribute",
                                        "segmentMatch",
                                        "op",
                                        "segmentMatch",
                                        "negate",
                                        false,
                                        "values",
                                        List.of(segmentKey))));
                instructions.add(instruction);
            }
        }
        if (!instructions.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(
                    "comment",
                    "Configure synthetic banking POC targeting while keeping the flag off");
            body.put("environmentKey", environment);
            body.put("instructions", instructions);
            requireStatus(
                    request(client, token, "PATCH", path, body, SEMANTIC_PATCH),
                    200,
                    "configure flag " + flag.key());
        }
    }

    private static void validateFlagShape(JsonNode current, FlagSpec expected) {
        if (!expected.kind().equals(current.path("kind").asText())) {
            throw new IllegalStateException(
                    "Existing flag " + expected.key() + " has an incompatible kind");
        }
        for (VariationSpec variation : expected.variations()) {
            if (findVariation(current, variation.value()).isEmpty()) {
                throw new IllegalStateException(
                        "Existing flag " + expected.key() + " is missing a required variation");
            }
        }
        JsonNode availability = current.path("clientSideAvailability");
        if (availability.path("usingEnvironmentId").asBoolean() != expected.clientSide()
                || availability.path("usingMobileKey").asBoolean()) {
            throw new IllegalStateException(
                    "Existing flag " + expected.key() + " has incompatible client availability");
        }
    }

    private static boolean hasSegmentRule(JsonNode segmentNode, SegmentSpec expected) {
        for (JsonNode rule : segmentNode.path("rules")) {
            for (JsonNode clause : rule.path("clauses")) {
                if ("user".equals(clause.path("contextKind").asText("user"))
                        && expected.attribute().equals(clause.path("attribute").asText())
                        && "in".equals(clause.path("op").asText())
                        && !clause.path("negate").asBoolean()
                        && containsJsonValue(clause.path("values"), expected.value())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasFlagSegmentRule(
            JsonNode environmentNode, String segmentKey, int targetVariationIndex) {
        for (JsonNode rule : environmentNode.path("rules")) {
            boolean correctVariation = rule.path("variation").asInt(-1) == targetVariationIndex;
            for (JsonNode clause : rule.path("clauses")) {
                if (correctVariation
                        && "segmentMatch".equals(clause.path("op").asText())
                        && containsJsonValue(clause.path("values"), segmentKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasIndividualTarget(
            JsonNode environmentNode, String contextKey, int targetVariationIndex) {
        for (JsonNode target : environmentNode.path("targets")) {
            if (target.path("variation").asInt(-1) == targetVariationIndex
                    && containsJsonValue(target.path("values"), contextKey)) {
                return true;
            }
        }
        for (JsonNode target : environmentNode.path("contextTargets")) {
            if ("user".equals(target.path("contextKind").asText())
                    && target.path("variation").asInt(-1) == targetVariationIndex
                    && containsJsonValue(target.path("values"), contextKey)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<JsonNode> findVariation(JsonNode flagNode, Object value) {
        JsonNode expected = MAPPER.valueToTree(value);
        for (JsonNode variation : flagNode.path("variations")) {
            if (variation.path("value").equals(expected)) {
                return Optional.of(variation);
            }
        }
        return Optional.empty();
    }

    private static String variationId(JsonNode flagNode, Object value) {
        return findVariation(flagNode, value)
                .map(node -> node.path("_id").asText())
                .filter(id -> !id.isBlank())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "LaunchDarkly did not return the expected variation ID"));
    }

    private static int variationIndex(JsonNode flagNode, Object value) {
        JsonNode expected = MAPPER.valueToTree(value);
        JsonNode variations = flagNode.path("variations");
        for (int index = 0; index < variations.size(); index++) {
            if (variations.get(index).path("value").equals(expected)) {
                return index;
            }
        }
        throw new IllegalStateException("LaunchDarkly did not return the expected variation");
    }

    private static boolean containsJsonValue(JsonNode values, Object expected) {
        JsonNode expectedNode = MAPPER.valueToTree(expected);
        for (JsonNode value : values) {
            if (value.equals(expectedNode)) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode readBody(ApiResponse response, String operation) {
        try {
            return MAPPER.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Invalid LaunchDarkly response for " + operation);
        }
    }

    private static ApiResponse request(
            HttpClient client,
            String token,
            String method,
            String path,
            Map<String, Object> body,
            String contentType)
            throws Exception {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(API_BASE + path))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", token)
                        .header("LD-API-Version", API_VERSION);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", contentType);
            builder.method(
                    method, HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        }

        for (int attempt = 0; attempt < 4; attempt++) {
            HttpResponse<String> response =
                    client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 429 || attempt == 3) {
                return new ApiResponse(response.statusCode(), response.body());
            }
            Thread.sleep(1000L << attempt);
        }
        throw new IllegalStateException("LaunchDarkly request retry loop ended unexpectedly");
    }

    private static void requireStatus(ApiResponse response, int expected, String operation) {
        if (response.status() != expected) {
            throw new IllegalStateException(operation + " failed with HTTP " + response.status());
        }
    }

    private static String apiPath(String... segments) {
        return "/"
                + Arrays.stream(segments)
                        .map(
                                value ->
                                        URLEncoder.encode(value, StandardCharsets.UTF_8)
                                                .replace("+", "%20"))
                        .reduce((left, right) -> left + "/" + right)
                        .orElseThrow();
    }

    private static FlagSpec boolFlag(
            String key,
            String name,
            String description,
            String falseName,
            String trueName,
            boolean clientSide,
            boolean targetValue,
            List<String> individualTargets,
            List<String> segmentTargets) {
        return new FlagSpec(
                key,
                name,
                "boolean",
                description,
                List.of(new VariationSpec(false, falseName), new VariationSpec(true, trueName)),
                false,
                targetValue,
                clientSide,
                individualTargets,
                segmentTargets);
    }

    private static FlagSpec stringFlag(
            String key,
            String name,
            String description,
            List<String> values,
            String safeValue,
            String targetValue,
            List<String> segmentTargets) {
        return new FlagSpec(
                key,
                name,
                "multivariate",
                description,
                values.stream().map(value -> new VariationSpec(value, title(value))).toList(),
                safeValue,
                targetValue,
                false,
                List.of(),
                segmentTargets);
    }

    private static void printPlan(String project, String environment, boolean apply) {
        System.out.printf(
                "%s LaunchDarkly bootstrap%nProject: %s%nEnvironment: %s%n",
                apply ? "APPLY" : "PREVIEW", project, environment);
        FLAGS.forEach(
                flag ->
                        System.out.printf(
                                "  flag %-28s kind=%-12s safe=%-10s clientSide=%s%n",
                                flag.key(), flag.kind(), flag.safeValue(), flag.clientSide()));
        SEGMENTS.forEach(
                segment ->
                        System.out.printf(
                                "  segment %-25s user.%s equals %s%n",
                                segment.key(), segment.attribute(), segment.value()));
        System.out.println("  targeting state: OFF for every flag; fallthrough/off: safe value");
        System.out.println("  maintenance-banner: reserved only; no application consumer yet");
    }

    private static Map<String, String> loadDotEnv() throws IOException {
        for (Path candidate : List.of(Path.of(".env"), Path.of("..", "..", ".env"))) {
            if (Files.isRegularFile(candidate)) {
                Map<String, String> values = new LinkedHashMap<>();
                for (String line : Files.readAllLines(candidate, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.isBlank() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int separator = trimmed.indexOf('=');
                    if (separator <= 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, separator).trim();
                    String value = unquote(trimmed.substring(separator + 1).trim());
                    values.put(key, value);
                }
                return Map.copyOf(values);
            }
        }
        return Map.of();
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Optional<String> argument(String[] args, String key) {
        for (int index = 0; index < args.length - 1; index++) {
            if (key.equals(args[index])) {
                return Optional.of(args[index + 1]);
            }
        }
        return Optional.empty();
    }

    private static String setting(Map<String, String> localSettings, String name, String fallback) {
        String environmentValue = System.getenv(name);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }
        String localValue = localSettings.get(name);
        return localValue == null || localValue.isBlank() ? fallback : localValue;
    }

    private static String requiredSetting(Map<String, String> localSettings, String name) {
        String value = setting(localSettings, name, "");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is required for --apply");
        }
        return value;
    }

    private static void requireKebabCase(String label, String key) {
        if (!KEBAB_CASE.matcher(key).matches()) {
            throw new IllegalArgumentException(label + " key must be kebab-case: " + key);
        }
    }

    private static void rejectProduction(String environment) {
        String normalized = environment.toLowerCase(Locale.ROOT);
        if (normalized.equals("prod")
                || normalized.equals("production")
                || normalized.contains("production")) {
            throw new IllegalArgumentException(
                    "Production-like LaunchDarkly environments are rejected");
        }
    }

    private static String title(String key) {
        return Arrays.stream(key.split("-"))
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .reduce((left, right) -> left + " " + right)
                .orElse(key);
    }

    private record ApiResponse(int status, String body) {}

    private record SegmentSpec(
            String key, String name, String description, String attribute, Object value) {
        Map<String, Object> clause() {
            return Map.of(
                    "contextKind",
                    "user",
                    "attribute",
                    attribute,
                    "op",
                    "in",
                    "negate",
                    false,
                    "values",
                    List.of(value));
        }
    }

    private record VariationSpec(Object value, String name) {
        Map<String, Object> payload() {
            return Map.of("value", value, "name", name);
        }
    }

    private record FlagSpec(
            String key,
            String name,
            String kind,
            String description,
            List<VariationSpec> variations,
            Object safeValue,
            Object targetValue,
            boolean clientSide,
            List<String> individualTargets,
            List<String> segmentTargets) {
        Map<String, Object> payload() {
            int safeIndex =
                    java.util.stream.IntStream.range(0, variations.size())
                            .filter(
                                    index ->
                                            MAPPER.valueToTree(variations.get(index).value())
                                                    .equals(MAPPER.valueToTree(safeValue)))
                            .findFirst()
                            .orElseThrow();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("key", key);
            payload.put("name", name);
            payload.put("kind", kind);
            payload.put("description", description + " Synthetic data only.");
            payload.put("tags", List.of("banking-poc", "synthetic-data", "kebab-case"));
            payload.put("variations", variations.stream().map(VariationSpec::payload).toList());
            payload.put("defaults", Map.of("onVariation", safeIndex, "offVariation", safeIndex));
            payload.put("temporary", true);
            payload.put("isFlagOn", false);
            payload.put(
                    "clientSideAvailability",
                    Map.of("usingEnvironmentId", clientSide, "usingMobileKey", false));
            return payload;
        }
    }
}
