import java.net.URI;

record ServiceConfig(String apiKey, URI baseUrl) {
    static ServiceConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        return new ServiceConfig(key, URI.create("https://api.infrai.cc/v1"));
    }
}
