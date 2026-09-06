package zsgrooms.modid.mixin;

import net.minecraft.block.NetherPortalBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NetherPortalBlock.AreaHelper.class)
public interface PortalAreaAccessor {
    @Accessor("lowerCorner")
    BlockPos zsgRooms$getLowerCorner();

    @Accessor("negativeDir")
    Direction zsgRooms$getNegativeDir();

    @Accessor("axis")
    Direction.Axis zsgRooms$getAxis();
}
