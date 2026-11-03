package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReplayCameraSamplerTest {
    @TempDir Path directory;

    @Test void ratesAreBoundedWithoutChangingFloatPrecision() throws Exception {
        for (int hz : new int[]{60, 120, 240}) {
            try (ReplayCameraTrack track = ReplayCameraTrack.create(directory, hz)) {
                ReplayCameraSampler sampler = new ReplayCameraSampler(hz);
                for (int ms = 0; ms < 1000; ms++) sampler.capture(track, ms * 1000000L, 0, 7, true, ms * 0.037f, -17.125f);
                List<Sample> samples = read(track);
                assertEquals(hz, samples.size());
                for (Sample sample : samples) {
                    assertEquals(sample.time * 0.037f, sample.yaw);
                    assertEquals(-17.125f, sample.pitch);
                    assertEquals(ReplayCameraTrack.AVAILABLE, sample.flags);
                }
            }
        }
    }

    @Test void slowRenderingDoesNotInventSamplesAndInvalidationBypassesRateLimit() throws Exception {
        try (ReplayCameraTrack track = ReplayCameraTrack.create(directory, 120)) {
            ReplayCameraSampler sampler = new ReplayCameraSampler(120);
            for (int ms : new int[]{0, 50, 100, 5000}) sampler.capture(track, ms * 1000000L, 0, 7, true, 5, 8);
            sampler.capture(track, 5001000000L, 0, 7, false, 0, 0);
            sampler.capture(track, 5002000000L, 0, 7, false, 0, 0);
            sampler.capture(track, 5003000000L, 1, 8, true, 11, 12);
            List<Sample> samples = read(track);
            assertEquals(6, samples.size());
            assertEquals(0, samples.get(4).flags);
            assertEquals(5001, samples.get(4).time);
            assertEquals(1, samples.get(5).world);
            assertEquals(8, samples.get(5).entity);
        }
    }

    @Test void subMillisecondInvalidationBecomesAGapInsteadOfBeingBridged() throws Exception {
        try (ReplayCameraTrack track = ReplayCameraTrack.create(directory, 60)) {
            ReplayCameraSampler sampler = new ReplayCameraSampler(60);
            sampler.capture(track, 0, 0, 7, true, 10, 20);
            sampler.capture(track, 500000, 0, 7, false, 0, 0);
            sampler.capture(track, 1000000, 0, 7, true, 10, 20);
            sampler.capture(track, 2000000, 0, 7, true, 10, 20);
            sampler.capture(track, 3000000, 0, 7, true, Float.NaN, 0);
            sampler.capture(track, 4000000, 0, 7, true, 0, 91);
            List<Sample> samples = read(track);
            assertEquals(4, samples.size());
            assertEquals(ReplayCameraTrack.GAP, samples.get(1).flags);
            assertEquals(ReplayCameraTrack.AVAILABLE, samples.get(2).flags);
            assertEquals(0, samples.get(3).flags);
        }
    }

    @Test void handoffBetweenRendersBreaksEvenAnUnchangedIdentityWithinTheSameRateBucket() throws Exception {
        for (int hz : new int[]{60, 120, 240}) {
            try (ReplayCameraTrack track = ReplayCameraTrack.create(directory, hz)) {
                ReplayCameraSampler sampler = new ReplayCameraSampler(hz);
                sampler.capture(track, 0, 0, 7, true, 10, 20);
                sampler.invalidate();
                sampler.capture(track, 500000, 0, 7, true, 10, 20);
                sampler.capture(track, 1000000, 0, 7, true, 50, 60);
                sampler.capture(track, 2000000, 0, 7, true, 50, 60);
                List<Sample> samples = read(track);
                assertEquals(3, samples.size());
                assertEquals(ReplayCameraTrack.GAP, samples.get(1).flags);
                assertEquals(1, samples.get(1).time);
                assertEquals(ReplayCameraTrack.AVAILABLE, samples.get(2).flags);
            }
        }
    }

    private List<Sample> read(ReplayCameraTrack track) throws Exception {
        track.finish();
        List<Sample> result = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(Files.newInputStream(track.completedFile()))) {
            assertEquals(0x5A43414D, input.readInt());
            assertEquals(1, input.readInt());
            assertTrue(input.readInt() > 0);
            while (input.available() > 0) result.add(new Sample(input));
        }
        return result;
    }

    private static final class Sample {
        final int time, world, entity, flags;
        final float yaw, pitch;
        Sample(DataInputStream input) throws Exception {
            time = input.readInt(); world = input.readInt(); entity = input.readInt(); flags = input.readInt();
            yaw = input.readFloat(); pitch = input.readFloat();
        }
    }
}
