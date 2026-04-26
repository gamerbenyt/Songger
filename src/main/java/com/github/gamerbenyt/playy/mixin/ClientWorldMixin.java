package com.github.gamerbenyt.playy.mixin;

import com.github.gamerbenyt.playy.Playy;
import com.github.gamerbenyt.playy.Util;
import com.github.gamerbenyt.playy.playing.SongHandler;
import com.github.gamerbenyt.playy.playing.Stage;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Used for debugging purposes

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(at = @At("HEAD"), method = "handleBlockUpdate(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)V", cancellable = true)
    public void onHandleBlockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        Stage stage = SongHandler.getInstance().stage;
        if (stage != null && !SongHandler.getInstance().building) {
            for (BlockPos nbp : stage.noteblockPositions.values()) {
                if (nbp.equals(pos)) {
                    BlockState oldState = Playy.MC.world.getBlockState(pos);
                    if (oldState.equals(state))
                        return;
                    Util.showChatMessage(String.format("§7Block in stage changed from §2%s §7to §2%s", oldState.toString(), state.toString()));
                    break;
                }
            }
        }
    }
}
