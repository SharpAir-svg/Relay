package com.relay.spectate;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;

/** Broadcast-style bar: head, name and hearts for each fighter around a centre badge with the match score. */
public final class TopBar implements HudElement {
	private static final int GOLD = 0xFFD9A441, GOLD_DK = 0xFFA8741A, PANEL = 0xE8101010;
	private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFF9A9A9A;
	private static final Identifier HEART_BG = Identifier.withDefaultNamespace("hud/heart/container");
	private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker tracker) {
		if (!RelayClient.hudVisible()) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int W = mc.getWindow().getGuiScaledWidth();

		int barW = Math.min(W - 16, 460), x0 = (W - barW) / 2, y0 = 3, h = 34;
		int cx = W / 2;
		fill(g, x0 - 2, y0 - 2, x0 + barW + 2, y0 + h + 2, GOLD);
		fill(g, x0, y0, x0 + barW, y0 + h, PANEL);

		side(g, font, mc, true, x0, y0, cx);
		side(g, font, mc, false, x0 + barW, y0, cx);

		// Centre badge with the match score underneath.
		fill(g, cx - 28, y0 - 2, cx + 28, y0 + 20, GOLD_DK);
		fill(g, cx - 26, y0, cx + 26, y0 + 18, 0xFF1A1208);
		centered(g, font, "PVP", cx, y0 + 2, GOLD);
		centered(g, font, "CHAMPS", cx, y0 + 9, WHITE);
		int slots = Math.max(2, Math.max(RelayClient.scoreA, RelayClient.scoreB));
		for (int i = 0; i < slots; i++) {
			boolean fa = i < RelayClient.scoreA, fb = i < RelayClient.scoreB;
			text(g, font, fa ? "◆" : "◇", cx - 12 - (i + 1) * 9 + 9, y0 + 23, fa ? WHITE : GRAY);
			text(g, font, fb ? "◆" : "◇", cx + 12 + i * 9, y0 + 23, fb ? WHITE : GRAY);
		}
	}

	/** Left side grows rightwards from the bar edge, right side leftwards. */
	private void side(GuiGraphicsExtractor g, Font font, Minecraft mc, boolean left, int edge, int y0, int cx) {
		String name = left ? RelayClient.nameA() : RelayClient.nameB();
		PlayerSkin skin = left ? RelayClient.skinA() : RelayClient.skinB();
		int seed = left ? RelayClient.seedA : RelayClient.seedB;
		int dir = left ? 1 : -1;
		int head = 22, pad = 5;

		int headX = left ? edge + pad : edge - pad - head;
		if (skin != null) PlayerFaceExtractor.extractRenderState(g, skin, headX, y0 + 4, head);
		else fill(g, headX, y0 + 4, headX + head, y0 + 4 + head, GOLD_DK);

		int textX = left ? headX + head + 5 : headX - 5;
		scaled(g, font, name.toUpperCase(), textX, y0 + 4, 1.4f, WHITE, !left);

		String sub = left ? ping(mc, name) + " • Seed " + seed : "Seed " + seed + " • " + ping(mc, name);
		scaled(g, font, sub, textX, y0 + 21, 0.9f, GRAY, !left);

		// Ten hearts, nearest the centre badge.
		float hp = RelayClient.healthOf(name);
		int step = 8, total = 10 * step;
		int hx = left ? cx - 36 - total : cx + 36;
		for (int i = 0; i < 10; i++) {
			int x = hx + i * step, y = y0 + 8;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_BG, x, y, 9, 9);
			float v = hp - i * 2;
			if (v >= 2) g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_FULL, x, y, 9, 9);
			else if (v >= 1) g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_HALF, x, y, 9, 9);
		}
	}

	private static String ping(Minecraft mc, String name) {
		if (mc.getConnection() == null) return "--";
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			if (info.getProfile().name().equalsIgnoreCase(name)) return info.getLatency() + "ms";
		}
		return "--";
	}

	// ---- drawing helpers ----
	private static void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int c) { g.fill(x1, y1, x2, y2, c); }

	private static void text(GuiGraphicsExtractor g, Font f, String s, int x, int y, int c) { g.text(f, s, x, y, c, true); }

	private static void centered(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int c) {
		text(g, f, s, cx - f.width(s) / 2, y, c);
	}

	/** Scaled text anchored at x, extending right (or left when alignRight). */
	private static void scaled(GuiGraphicsExtractor g, Font f, String s, int x, int y, float sc, int c, boolean alignRight) {
		float ox = alignRight ? x - f.width(s) * sc : x;
		g.pose().pushMatrix();
		g.pose().translate(ox, y);
		g.pose().scale(sc, sc);
		text(g, f, s, 0, 0, c);
		g.pose().popMatrix();
	}
}
