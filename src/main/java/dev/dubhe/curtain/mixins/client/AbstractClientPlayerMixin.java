package dev.dubhe.curtain.mixins.client;

import dev.dubhe.curtain.features.player.client.CurtainClientSkinManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void curtain$getSkinTextureLocation(CallbackInfoReturnable<ResourceLocation> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        ResourceLocation skin = CurtainClientSkinManager.getSkin(player.getUUID());
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void curtain$getModelName(CallbackInfoReturnable<String> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        String modelName = CurtainClientSkinManager.getModelName(player.getUUID());
        if (modelName != null) {
            cir.setReturnValue(modelName);
        }
    }
}
