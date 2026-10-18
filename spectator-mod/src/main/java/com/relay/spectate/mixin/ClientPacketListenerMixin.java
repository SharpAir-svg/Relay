package com.relay.spectate.mixin;

import com.relay.spectate.RelayClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entity event 35 is "totem of undying used"; the client has no other hook for it. */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleEntityEvent", at = @At("HEAD"))
	private void relay$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		var level = Minecraft.getInstance().level;
		if (level != null) RelayClient.onEntityEvent(packet.getEntity(level), packet.getEventId());
	}
}
