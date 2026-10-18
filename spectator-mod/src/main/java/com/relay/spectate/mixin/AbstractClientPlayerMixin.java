package com.relay.spectate.mixin;

import com.relay.spectate.GhostSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A replay ghost needs its own UUID (the level refuses duplicates), so the game can't look its skin up by UUID.
 * This hands it the real player's skin directly.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin implements GhostSkin {
	@Unique
	private PlayerSkin relay$skin;

	@Override
	public void relay$setSkin(PlayerSkin skin) {
		this.relay$skin = skin;
	}

	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void relay$overrideSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		if (relay$skin != null) cir.setReturnValue(relay$skin);
	}
}
