import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class DummyMonitoredService {
    private static final int PORT = Integer.parseInt(env("PORT", "8080"));
    private static final AtomicReference<Scenario> CURRENT_SCENARIO =
        new AtomicReference<>(Scenario.DEFAULT);

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/actuator/health", DummyMonitoredService::handleHealth);
        server.createContext("/internal/monitoring/last-run", DummyMonitoredService::handleLastRun);
        server.createContext("/internal/test-control", DummyMonitoredService::handleTestControl);
        server.createContext("/", DummyMonitoredService::handleRoot);
        server.start();

        System.out.printf("dummy-monitored-service listening on port %d%n", PORT);
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        Scenario scenario = CURRENT_SCENARIO.get();
        String healthStatus = switch (scenario) {
            case HEALTH_DOWN -> "DOWN";
            case HEALTHY, LAST_RUN_UNAVAILABLE, RUN_FAILED -> "UP";
            case DEFAULT -> env("DUMMY_HEALTH_STATUS", "UP").toUpperCase();
        };
        int statusCode = "UP".equals(healthStatus) ? 200 : 503;

        writeJson(exchange, statusCode, "{\"status\":\"" + escapeJson(healthStatus) + "\"}");
    }

    private static void handleLastRun(HttpExchange exchange) throws IOException {
        Scenario scenario = CURRENT_SCENARIO.get();
        if (scenario == Scenario.LAST_RUN_UNAVAILABLE) {
            writeJson(exchange, 503, "{\"error\":\"simulated last-run unavailable\"}");
            return;
        }

        String service = env("DUMMY_SERVICE_NAME", "dummy-monitored-service");
        String environment = env("DUMMY_ENVIRONMENT", "demo");
        String timezone = env("DUMMY_TIMEZONE", "Asia/Seoul");
        String lastRunDate = scenario == Scenario.HEALTHY
            ? LocalDate.now().toString()
            : env("DUMMY_LAST_RUN_DATE", LocalDate.now().toString());
        String runStatus = switch (scenario) {
            case RUN_FAILED -> "FAILED";
            case HEALTHY, HEALTH_DOWN -> "SENT";
            case DEFAULT -> env("DUMMY_RUN_STATUS", "SENT").toUpperCase();
            case LAST_RUN_UNAVAILABLE -> throw new IllegalStateException("Handled before payload generation");
        };
        String sentAt = env("DUMMY_SENT_AT", OffsetDateTime.now().toString());
        String error = scenario == Scenario.RUN_FAILED
            ? "Simulated run failure"
            : scenario == Scenario.HEALTHY
                ? ""
                : env("DUMMY_ERROR", "");

        String payload = """
            {
              "service":"%s",
              "environment":"%s",
              "timezone":"%s",
              "lastRunDate":"%s",
              "status":"%s",
              "sentAt":"%s",
              "error":%s
            }
            """.formatted(
            escapeJson(service),
            escapeJson(environment),
            escapeJson(timezone),
            escapeJson(lastRunDate),
            escapeJson(runStatus),
            escapeJson(sentAt),
            error.isBlank() ? "null" : "\"" + escapeJson(error) + "\""
        ).replace("\n", "").replace("  ", "");

        writeJson(exchange, 200, payload);
    }

    private static void handleTestControl(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            writeScenarioResponse(exchange);
            return;
        }

        if (!"PUT".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
            writeJson(exchange, 405, "{\"error\":\"use GET, PUT, or POST\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String prefix = "/internal/test-control/";
        if (!path.startsWith(prefix) || path.length() == prefix.length()) {
            writeJson(exchange, 400, "{\"error\":\"scenario path is required\"}");
            return;
        }

        String requested = path.substring(prefix.length())
            .replace('-', '_')
            .toUpperCase(Locale.ROOT);
        if ("RESET".equals(requested)) {
            requested = "DEFAULT";
        }

        try {
            CURRENT_SCENARIO.set(Scenario.valueOf(requested));
            writeScenarioResponse(exchange);
        } catch (IllegalArgumentException exception) {
            writeJson(
                exchange,
                400,
                "{\"error\":\"unsupported scenario\",\"supported\":\"healthy,last-run-unavailable,health-down,run-failed,reset\"}"
            );
        }
    }

    private static void writeScenarioResponse(HttpExchange exchange) throws IOException {
        writeJson(
            exchange,
            200,
            "{\"scenario\":\"" + CURRENT_SCENARIO.get().name() + "\"}"
        );
    }

    private static void handleRoot(HttpExchange exchange) throws IOException {
        writeJson(
            exchange,
            200,
            "{\"service\":\"dummy-monitored-service\",\"endpoints\":[\"/actuator/health\",\"/internal/monitoring/last-run\",\"/internal/test-control\"]}"
        );
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String payload) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, body.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    private enum Scenario {
        DEFAULT,
        HEALTHY,
        LAST_RUN_UNAVAILABLE,
        HEALTH_DOWN,
        RUN_FAILED
    }
}
