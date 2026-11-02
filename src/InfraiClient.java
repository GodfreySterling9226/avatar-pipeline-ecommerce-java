import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

final class InfraiClient {
    private final ServiceConfig config;
    private final HttpClient http;

    InfraiClient(ServiceConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    String upload(byte[] file, String filename, String idempotencyKey) {
        String body = "{\"file\":\"" + encoded(file) + "\",\"filename\":\"" + json(filename) + "\"}";
        return imageReference(post("/v1/image/upload", body, idempotencyKey));
    }

    String smartCrop(String image, String aspect) {
        return imageReference(post("/v1/image/smart_crop", "{\"image\":\"" + json(image) + "\",\"aspect\":\"" + json(aspect) + "\"}", null));
    }

    String resize(String image, int width, int height, String idempotencyKey) {
        String body = "{\"image\":\"" + json(image) + "\",\"width\":" + width + ",\"height\":" + height + ",\"fit\":\"cover\",\"enlarge\":false,\"format\":\"webp\",\"store\":true}";
        return imageReference(post("/v1/image/resize", body, idempotencyKey));
    }

    String updateUser(String userId, String image) {
        return request("PATCH", "/v1/auth/user/update/" + encodePath(userId), "{\"user_id\":\"" + json(userId) + "\",\"metadata\":{\"image\":\"" + json(image) + "\"}}", null);
    }

    private String post(String path, String body, String idempotencyKey) {
        return request("POST", path, body, idempotencyKey);
    }

    private String request(String method, String path, String body, String idempotencyKey) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            for (int attempt = 0; attempt < 4; attempt++) {
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                String envelope = response.body();
                if (response.statusCode() == 429 && attempt < 3) {
                    long delay = retryDelay(response, attempt);
                    Thread.sleep(delay);
                    continue;
                }
                Map<?, ?> decoded = envelope(envelope);
                if (response.statusCode() < 200 || response.statusCode() >= 300
                        || !Boolean.TRUE.equals(decoded.get("ok"))) {
                    throw new InfraiException("HTTP " + response.statusCode() + ": " + envelope);
                }
                return envelope;
            }
            throw new InfraiException("retry budget exhausted");
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException(e.getMessage());
        }
    }

    private static Map<?, ?> envelope(String body) {
        Object value = Json.parse(body);
        if (!(value instanceof Map<?, ?> result)) throw new InfraiException("Invalid response envelope");
        return result;
    }

    private static String imageReference(String body) {
        Object data = envelope(body).get("data");
        if (data instanceof Map<?, ?> image) {
            for (String field : new String[]{"url", "image", "id"}) {
                if (image.get(field) instanceof String reference && !reference.isBlank()) return reference;
            }
        }
        if (data instanceof String reference && !reference.isBlank()) return reference;
        throw new InfraiException("Response contains no image reference: " + body);
    }

    private static long retryDelay(HttpResponse<String> response, int attempt) {
        return response.headers().firstValue("Retry-After").map(v -> Long.parseLong(v) * 1000L).orElse(250L * (1L << attempt));
    }

    private static String encoded(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String encodePath(String value) { return value.replace("/", "%2F"); }
}

final class InfraiException extends RuntimeException {
    InfraiException(String message) { super(message); }
}
