import java.nio.file.Files;
import java.nio.file.Path;

public final class AvatarPipelineExample {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: AvatarPipelineExample <image-file> <user-id>");
        ServiceConfig config = ServiceConfig.fromEnvironment();
        InfraiClient client = new InfraiClient(config);
        AvatarResult result = new AvatarPipeline(new AvatarGateway() {
            public String upload(byte[] file, String filename, String operationId) { return client.upload(file, filename, operationId); }
            public String smartCrop(String image, String aspect) { return client.smartCrop(image, aspect); }
            public String resize(String image, int width, int height, String operationId) { return client.resize(image, width, height, operationId); }
            public String updateUser(String userId, String image) { return client.updateUser(userId, image); }
        }).process(new AvatarInput(args[1], Path.of(args[0]).getFileName().toString(), Files.readAllBytes(Path.of(args[0]))));
        System.out.println(result.status() + " " + result.width() + "x" + result.height() + " user=" + result.userId());
    }
}
