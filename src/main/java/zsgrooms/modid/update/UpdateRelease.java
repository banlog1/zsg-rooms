package zsgrooms.modid.update;

public final class UpdateRelease {
    public final String version;
    public final String releaseUrl;
    public final String downloadUrl;
    public final String sha256;
    public final String checksumUrl;
    public final String fileName;

    public UpdateRelease(String version, String releaseUrl, String downloadUrl, String sha256,
                         String checksumUrl, String fileName) {
        this.version = version;
        this.releaseUrl = releaseUrl;
        this.downloadUrl = downloadUrl;
        this.sha256 = sha256;
        this.checksumUrl = checksumUrl;
        this.fileName = fileName;
    }
}
