package zsgrooms.modid.replay;

/** One-second observational HUD window. Lower FPS is not counted as capture loss. */
final class ReplayCaptureHealth {
    private long lastNanos = -1, lastSamples;
    private String text;

    boolean due(long now) { return lastNanos < 0 || now - lastNanos >= 1_000_000_000L; }

    String update(long now, String level, int cameraHz, String cameraStatus, long[] camera,
                  ReplayBuffer.Snapshot packets, String detailStatus) {
        String rate = "Camera: Standard";
        int pressure = Math.max(percent(packets.bytes, packets.byteLimit), percent(packets.records, packets.recordLimit));
        if (cameraHz > 0) {
            if (camera == null) rate = "Camera: " + cameraStatus;
            else {
                String hz = lastNanos < 0 || now <= lastNanos ? "--"
                        : Long.toString(Math.round(Math.max(0, camera[0] - lastSamples) * 1_000_000_000D / (now - lastNanos)));
                rate = "Camera: " + hz + "/" + cameraHz + " Hz | Dropped: " + camera[1];
                pressure = Math.max(pressure, percent(camera[2], camera[3]));
                lastSamples = camera[0];
                if (!"recording".equals(cameraStatus)) rate += " (" + cameraStatus + ")";
            }
        }
        lastNanos = now;
        text = level + " | " + rate + " | Queue: " + pressure + "%";
        if (!"off".equals(detailStatus) && !"recording".equals(detailStatus)) text += " | Detail: " + detailStatus;
        return text;
    }

    String text() { return text; }

    private static int percent(long value, long limit) {
        return limit <= 0 ? 0 : (int) Math.min(100, Math.max(0, value * 100 / limit));
    }
}
