import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public class DummyMonitoredService {
    private static final int PORT = Integer.parseInt(env("PORT", "8080"));

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/actuator/health", DummyMonitoredService::handleHealth);
        server.createContext("/internal/monitoring/last-run", DummyMonitoredService::handleLastRun);
        server.createContext("/", DummyMonitoredService::handleRoot);
        server.start();

        System.out.printf("dummy-monitored-service listening on port %d%n", PORT);
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        String healthStatus = env("DUMMY_HEALTH_STATUS", "UP").toUpperCase();
        int statusCode = "UP".equals(healthStatus) ? 200 : 503;

        writeJson(exchange, statusCode, "{\"status\":\"" + escapeJson(healthStatus) + "\"}");
    }

    private static void handleLastRun(HttpExchange exchange) throws IOException {
        String service = env("DUMMY_SERVICE_NAME", "dummy-monitored-service");
        String environment = env("DUMMY_ENVIRONMENT", "demo");
        String timezone = env("DUMMY_TIMEZONE", "Asia/Seoul");
        String lastRunDate = env("DUMMY_LAST_RUN_DATE", LocalDate.now().toString());
        String runStatus = env("DUMMY_RUN_STATUS", "SENT").toUpperCase();
        String sentAt = env("DUMMY_SENT_AT", OffsetDateTime.now().toString());
        String error = env("DUMMY_ERROR", "");

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

    private static void handleRoot(HttpExchange exchange) throws IOException {
        writeJson(
            exchange,
            200,
            "{\"service\":\"dummy-monitored-service\",\"endpoints\":[\"/actuator/health\",\"/internal/monitoring/last-run\"]}"
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
}
