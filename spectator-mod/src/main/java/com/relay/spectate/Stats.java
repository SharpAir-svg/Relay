package com.relay.spectate;

import net.minecraft.world.entity.player.Player;

/**
 * Client-side estimate of the numbers shown on the round breakdown. The client
 * never sees the server's damage events, so everything is inferred from
 * observed health changes; treat it as close, not exact.
 */
public final class Stats {
	public static final class Side {
		public double damage, regen;
		public int hits, swings, crits, totems;
		float lastHealth = -1;
		boolean lastSwinging;
		int ignoreRegenTicks;

		public double accuracy() {
			return swings == 0 ? 0 : 100.0 * hits / swings;
		}
	}

	public final Side a = new Side(), b = new Side();

	public void tick(Player pa, Player pb) {
		step(a, b, pa, pb);
		step(b, a, pb, pa);
	}

	/** {@code me} is the fighter being measured, {@code them} the opponent. */
	private void step(Side mine, Side theirs, Player me, Player them) {
		float hp = me.getHealth() + me.getAbsorptionAmount();
		if (mine.lastHealth >= 0) {
			float delta = hp - mine.lastHealth;
			// Only credit damage when the opponent was actually in reach.
			if (delta < 0 && me.distanceTo(them) < 7.0f) {
				theirs.damage += -delta;
				theirs.hits++;
				boolean falling = !them.onGround() && them.getDeltaMovement().y < 0;
				if (falling) theirs.crits++;
			} else if (delta > 0 && mine.ignoreRegenTicks <= 0) {
				mine.regen += delta;
			}
		}
		mine.lastHealth = hp;
		if (mine.ignoreRegenTicks > 0) mine.ignoreRegenTicks--;

		if (me.swinging && !mine.lastSwinging) mine.swings++;
		mine.lastSwinging = me.swinging;
	}

	/** A totem refills health instantly; keep that out of the regen figure. */
	public void totemPopped(boolean isA) {
		Side s = isA ? a : b;
		s.totems++;
		s.ignoreRegenTicks = 3;
	}
}
