package zsgrooms.modid;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class GameRuleAuditTest {
    private static final Identifier BASTION = new Identifier("minecraft", "chests/bastion_other");

    @AfterEach void clearRuleState() {
        BastionIronGuarantee.configure(false);
    }

    @Test void allGameplayRuleCombinationsSurviveSerializationAndApplication() throws Exception {
        Field[] fields = Arrays.stream(RoomRuleSettings.class.getFields())
                .filter(field -> field.getType() == boolean.class && !field.getName().equals("resetTournament"))
                .toArray(Field[]::new);
        assertEquals(13, fields.length);
        InGame game = new InGame("123|structure:manual", "audit", InGame.SeedType.FIXED, false);
        for (int mask = 0; mask < (1 << fields.length); mask++) {
            RoomRuleSettings rules = RoomRuleSettings.capture(game);
            for (int i = 0; i < fields.length; i++) fields[i].setBoolean(rules, (mask & (1 << i)) != 0);
            String json = rules.toJson();
            RoomRuleSettings decoded = RoomRuleSettings.fromJson(json);
            assertNotNull(decoded);
            decoded.applyTo(game);
            assertEquals(json, RoomRuleSettings.capture(game).toJson(), "Combination " + mask);
        }
    }

    @Test void ironTopUpDoesNotOverwriteAFullChestAndCanUseTheNextChest() {
        SimpleInventory full = new SimpleInventory(27);
        for (int i = 0; i < full.size(); i++) full.setStack(i, new ItemStack(Items.GOLD_INGOT, 64));
        BastionIronGuarantee.configure(true);
        BastionIronGuarantee.topUpFirstChest(full, BASTION, 123L);
        for (int i = 0; i < full.size(); i++) {
            assertEquals(Items.GOLD_INGOT, full.getStack(i).getItem());
            assertEquals(64, full.getStack(i).getCount());
        }
        assertTrue(BastionIronGuarantee.shouldInspect(BASTION));
        SimpleInventory next = new SimpleInventory(27);
        BastionIronGuarantee.topUpFirstChest(next, BASTION, 456L);
        assertEquals(27, BastionIronGuarantee.countIronUnits(next));
        assertFalse(BastionIronGuarantee.shouldInspect(BASTION));
    }

    @Test void ironGuaranteeIsOncePerConfigurationAndResetRearmsIt() {
        BastionIronGuarantee.configure(true);
        SimpleInventory first = new SimpleInventory(27);
        first.setStack(0, new ItemStack(Items.IRON_INGOT, 5));
        BastionIronGuarantee.topUpFirstChest(first, BASTION, 1L);
        SimpleInventory second = new SimpleInventory(27);
        BastionIronGuarantee.topUpFirstChest(second, BASTION, 2L);
        assertEquals(0, BastionIronGuarantee.countIronUnits(second));
        BastionIronGuarantee.configure(true);
        BastionIronGuarantee.topUpFirstChest(second, BASTION, 2L);
        assertEquals(27, BastionIronGuarantee.countIronUnits(second));
    }
}
