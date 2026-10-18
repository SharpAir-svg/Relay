package com.relay.spectate;

import net.minecraft.world.entity.player.PlayerSkin;

/** Lets a replay ghost wear the real player's skin; implemented onto AbstractClientPlayer by a mixin. */
public interface GhostSkin {
	void relay$setSkin(PlayerSkin skin);
}
