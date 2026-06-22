package dev.i726.rocky.mixin;

import dev.i726.rocky.utils.CrystalUtils;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EndCrystalItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static dev.i726.rocky.Rocky.mc;

@Mixin(EndCrystalItem.class)
public class EndCrystalItemMixin {

    @Unique
    private Vec3d getPlayerLookVec(PlayerEntity p) {
        return RenderUtils.getPlayerLookVec(p);
    }

    @Unique
    private Vec3d getClientLookVec() {
        assert mc.player != null;
        return getPlayerLookVec(mc.player);
    }

    @Unique
    private boolean isBlock(Block b, BlockPos p) {
        return getBlockState(p).getBlock() == b;
    }

    @Unique
    private BlockState getBlockState(BlockPos p) {
        return mc.world.getBlockState(p);
    }

    @Unique
    private boolean canPlaceCrystalServer(BlockPos blockPos) {
        BlockState blockState = mc.world.getBlockState(blockPos);
        if (!blockState.isOf(Blocks.OBSIDIAN) && !blockState.isOf(Blocks.BEDROCK))
            return false;
        return CrystalUtils.canPlaceCrystalClientAssumeObsidian(blockPos);
    }

    @Inject(method = "useOnBlock", at = @At("HEAD"))
    private void onUse(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
    }
}
