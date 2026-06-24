package dev.i726.rocky.mixin;

import dev.i726.rocky.utils.CrystalUtils;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static dev.i726.rocky.Rocky.mc;

@Mixin(EndCrystalItem.class)
public class EndCrystalItemMixin {

    @Unique
    private Vec3 getPlayerLookVec(Player p) {
        return RenderUtils.getPlayerLookVec(p);
    }

    @Unique
    private Vec3 getClientLookVec() {
        assert mc.player != null;
        return getPlayerLookVec(mc.player);
    }

    @Unique
    private boolean isBlock(Block b, BlockPos p) {
        return getBlockState(p).getBlock() == b;
    }

    @Unique
    private BlockState getBlockState(BlockPos p) {
        return mc.level.getBlockState(p);
    }

    @Unique
    private boolean canPlaceCrystalServer(BlockPos blockPos) {
        BlockState blockState = mc.level.getBlockState(blockPos);
        if (!blockState.is(Blocks.OBSIDIAN) && !blockState.is(Blocks.BEDROCK))
            return false;
        return CrystalUtils.canPlaceCrystalClientAssumeObsidian(blockPos);
    }

    @Inject(method = "useOn", at = @At("HEAD"))
    private void onUse(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
    }
}
