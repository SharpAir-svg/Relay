package com.relay.spectate;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Runs on both sides: registers the packets and, on a server, answers inventory requests. */
public final class RelayMain implements ModInitializer {
	@Override
	public void onInitialize() {
		RelayNet.registerTypes();
		ServerPlayNetworking.registerGlobalReceiver(RelayNet.Request.TYPE, (payload, ctx) -> {
			ServerPlayer asker = ctx.player();
			if (!asker.isSpectator()) return; // only spectators get to see other players' inventories
			for (String name : new String[] { payload.a(), payload.b() }) {
				ServerPlayer target = ctx.server().getPlayerList().getPlayerByName(name);
				if (target != null) ServerPlayNetworking.send(asker, new RelayNet.Summary(name, InventoryScan.count(target)));
			}
		});
	}
}
