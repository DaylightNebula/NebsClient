package dsh.nebsclient.mod.mixin;

import dsh.nebsclient.mod.runtime.Hooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets nebs hold the attack button. Vanilla passes {@code attackKeyDown && mouseGrabbed} to
 * {@code continueAttack}, and a background window never grabs the mouse, so without this any
 * block-breaking nebs starts is aborted on the next tick.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true)
    private boolean nebs$forceAttack(boolean leftClick) {
        return leftClick || Hooks.forceAttack;
    }
}
