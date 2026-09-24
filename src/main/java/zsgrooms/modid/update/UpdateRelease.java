package zsgrooms.modid.update;

import java.util.ArrayList;
import java.util.List;

public final class UpdateRelease {
    public final String version;
    public final String releaseUrl;
    public final String downloadUrl;
    public final String sha256;
    public final String checksumUrl;
    public final String fileName;
    public final UpdateArtifact viewer;

    public UpdateRelease(String version, String releaseUrl, String downloadUrl, String sha256,
                         String checksumUrl, String fileName) {
        this(version, releaseUrl, downloadUrl, sha256, checksumUrl, fileName, null);
    }

    UpdateRelease(String version, String releaseUrl, String downloadUrl, String sha256,
                  String checksumUrl, String fileName, UpdateArtifact viewer) {
        this.version = version;
        this.releaseUrl = releaseUrl;
        this.downloadUrl = downloadUrl;
        this.sha256 = sha256;
        this.checksumUrl = checksumUrl;
        this.fileName = fileName;
        this.viewer = viewer;
    }

    List<UpdateArtifact> updates(String coreVersion, String viewerVersion) {
        List<UpdateArtifact> updates = new ArrayList<>();
        if (UpdateManager.isNewer(this.version, coreVersion)) {
            updates.add(new UpdateArtifact("zsg-rooms", "ZSG Rooms", this.version, this.downloadUrl,
                    this.sha256, this.checksumUrl, this.fileName));
        }
        if (this.viewer != null && viewerVersion != null
                && UpdateManager.isNewer(this.viewer.version, viewerVersion)) {
            updates.add(this.viewer);
        }
        return updates;
    }
}
