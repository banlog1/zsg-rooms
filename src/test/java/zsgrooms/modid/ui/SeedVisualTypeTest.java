package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.ZsgSeedBridge;

import static org.junit.jupiter.api.Assertions.*;

class SeedVisualTypeTest {
    @Test void roomsPortalUsesExistingPortalArtwork() {
        FilterCatalog.Entry entry = FilterCatalog.find("rooms-ruined-portal-v5");
        assertEquals(FilterCatalog.Group.ROOMS, entry.group);
        assertEquals("rp", entry.image);
        assertEquals(SeedVisualType.PORTAL, SeedVisualType.forFilter(entry.id));
        assertArrayEquals(new String[]{"rp"}, SeedVisualType.forFilter(entry.id).images);
    }

    @Test void roomsTreasureUsesExistingArtworkAndPreservesTheUnknownFilterFallback() {
        FilterCatalog.Entry entry = FilterCatalog.find("rooms-buried-treasure-v5");
        assertEquals(FilterCatalog.Group.ROOMS, entry.group);
        assertEquals("bt", entry.image);
        assertEquals(SeedVisualType.TREASURE, SeedVisualType.forFilter(entry.id));
        assertArrayEquals(new String[]{"bt"}, SeedVisualType.forFilter(entry.id).images);
        assertEquals("zsg", FilterCatalog.find("unrecognized").id);
    }

    @Test void everyPickerFilterHasAnExplicitVisualCategory() {
        for (FilterCatalog.Entry entry : FilterCatalog.ENTRIES) {
            assertNotEquals(SeedVisualType.UNKNOWN, SeedVisualType.forFilter(entry.id), entry.id);
        }
        assertEquals(SeedVisualType.UNKNOWN, SeedVisualType.forFilter("unrecognized"));
        assertEquals(SeedVisualType.UNKNOWN, SeedVisualType.forFilter(null));
        assertEquals(SeedVisualType.MANUAL, SeedVisualType.forFilter("manual:123"));
    }

    @Test void mixedModeArtworkUsesActualSeedMetadata() {
        String seed = "123|structure:rooms-temple-v5|iron:4|selection:rooms-mix";
        assertEquals(SeedVisualType.TEMPLE, SeedVisualType.forFilter(ZsgSeedBridge.resolveStructure(seed)));
        assertEquals("rooms-mix", ZsgSeedBridge.seedSpecificationFromSeed(seed));
    }

    @Test void opVariantsKeepTheirCategoryAndHaveDistinctLabels() {
        assertEquals(SeedVisualType.TREASURE, SeedVisualType.forFilter("zsgop"));
        assertEquals("Buried Treasure OP", SeedVisualType.TREASURE.labelFor("zsgop"));
        assertEquals("Desert Temple", SeedVisualType.TEMPLE.labelFor("rooms-temple-v5"));
        assertEquals(0, SeedVisualType.RANDOM.images.length);
        assertEquals(0, SeedVisualType.MIXED.images.length);
        assertEquals(2, SeedVisualType.TEMPLE.images.length);
    }

    @Test void loadingContextIsOneShotAndCanBeCancelledBeforeAScreenExists() {
        RoomLoadingArtwork.prepare("123|structure:rooms-village-v5");
        assertNotNull(RoomLoadingArtwork.capture());
        assertNull(RoomLoadingArtwork.capture());
        RoomLoadingArtwork.prepare("456|structure:rooms-temple-v5");
        RoomLoadingArtwork.cancel();
        assertNull(RoomLoadingArtwork.capture());
    }
}
