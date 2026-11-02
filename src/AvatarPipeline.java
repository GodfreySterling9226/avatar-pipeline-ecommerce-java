import java.util.UUID;

record AvatarInput(String userId, String filename, byte[] file) {}
record AvatarResult(String userId, String status, int width, int height, String imageReference) {}

interface AvatarGateway {
    String upload(byte[] file, String filename, String operationId);
    String smartCrop(String image, String aspect);
    String resize(String image, int width, int height, String operationId);
    String updateUser(String userId, String image);
}

final class AvatarPipeline {
    private final AvatarGateway gateway;

    AvatarPipeline(AvatarGateway gateway) { this.gateway = gateway; }

    AvatarResult process(AvatarInput input) {
        if (input.userId().isBlank() || input.file().length == 0) throw new IllegalArgumentException("avatar input is incomplete");
        String operationId = UUID.nameUUIDFromBytes((input.userId() + ":" + input.filename()).getBytes()).toString();
        String uploaded = gateway.upload(input.file(), input.filename(), operationId);
        String cropped = gateway.smartCrop(uploaded, "1:1");
        String resized = gateway.resize(cropped, 256, 256, operationId);
        gateway.updateUser(input.userId(), resized);
        return new AvatarResult(input.userId(), "READY", 256, 256, resized);
    }
}
