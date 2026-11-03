package zsgrooms.modid.replay;

import net.minecraft.item.ItemStack;

/** Read-only renderer cache access: no fields are ever changed during capture. */
public interface ReplayPresentationHeldAccess {
    ItemStack zsgPresentation$mainHand();
    ItemStack zsgPresentation$offHand();
    float zsgPresentation$mainEquip();
    float zsgPresentation$previousMainEquip();
    float zsgPresentation$offEquip();
    float zsgPresentation$previousOffEquip();
}
