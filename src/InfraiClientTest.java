import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public final class InfraiClientTest {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger uploads = new AtomicInteger();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int status = 200;
            String reply;
            if (path.endsWith("upload") && uploads.getAndIncrement() == 0) {
                status = 429; reply = "{\"ok\": false, \"error\": {\"code\":\"RATE_LIMIT\"}}";
                exchange.getResponseHeaders().set("Retry-After", "0");
            } else if (path.endsWith("upload")) {
                reply = "{\"ok\": true, \"data\": {\"url\":\"https://example.test/upload\"}}";
            } else if (path.endsWith("smart_crop")) {
                if (!"https://example.test/upload".equals(((java.util.Map<?, ?>) Json.parse(body)).get("image"))) throw new AssertionError(body);
                reply = "{\"ok\":true,\"data\":{\"url\":\"https://example.test/crop\"}}";
            } else if (path.endsWith("resize")) {
                if (!"https://example.test/crop".equals(((java.util.Map<?, ?>) Json.parse(body)).get("image"))) throw new AssertionError(body);
                reply = "{\"ok\":true,\"data\":{\"url\":\"https://example.test/resized\"}}";
            } else {
                reply = "{\"ok\" : false, \"error\": {\"message\":\"rejected\"},\"data\":null}";
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            InfraiClient client = new InfraiClient(new ServiceConfig("test", URI.create("http://127.0.0.1:" + server.getAddress().getPort())));
            String uploaded = client.upload(new byte[]{1}, "test", "test");
            String resized = client.resize(client.smartCrop(uploaded, "1:1"), 256, 256, "test");
            if (!resized.equals("https://example.test/resized") || uploads.get() != 2) throw new AssertionError();
            try { client.updateUser("test", resized); throw new AssertionError("accepted error envelope"); }
            catch (InfraiException expected) { }
            System.out.println("PASS: image references, whitespace errors, and 429 retry");
        } finally { server.stop(0); }
    }
}
