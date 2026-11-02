public final class AvatarPipelineTest {
    public static void main(String[] args) {
        AvatarGateway fake = new AvatarGateway() {
            public String upload(byte[] file, String filename, String operationId) { return "uploaded-avatar"; }
            public String smartCrop(String image, String aspect) { if (!"1:1".equals(aspect)) throw new AssertionError(aspect); return "square-avatar"; }
            public String resize(String image, int width, int height, String operationId) { if (width != 256 || height != 256) throw new AssertionError("size"); return "avatar-256"; }
            public String updateUser(String userId, String image) { if (!"learner-42".equals(userId) || !"avatar-256".equals(image)) throw new AssertionError("user write"); return "updated"; }
        };
        AvatarResult result = new AvatarPipeline(fake).process(new AvatarInput("learner-42", "portrait.jpg", new byte[400]));
        if (!"READY".equals(result.status()) || result.width() != 256 || result.height() != 256) throw new AssertionError(result);
        System.out.println("PASS: learner-42 avatar is READY at 256x256");
    }
}
