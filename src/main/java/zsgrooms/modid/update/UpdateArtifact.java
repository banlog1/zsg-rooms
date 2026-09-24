package zsgrooms.modid.update;

public final class UpdateArtifact {
    public final String modId;
    public final String label;
    public final String version;
    public final String downloadUrl;
    public final String sha256;
    public final String checksumUrl;
    public final String fileName;

    UpdateArtifact(String modId, String label, String version, String downloadUrl, String sha256,
                   String checksumUrl, String fileName) {
        this.modId = modId;
        this.label = label;
        this.version = version;
        this.downloadUrl = downloadUrl;
        this.sha256 = sha256;
        this.checksumUrl = checksumUrl;
        this.fileName = fileName;
    }
}
