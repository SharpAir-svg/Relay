package com.relay.spectate;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The post-round summary. All drawing goes through the few calls in
 * {@link #fill}, {@link #text} and {@link #centered} so any rendering API
 * change between Minecraft versions is a one-place fix.
 */
public final class BreakdownScreen extends Screen {
	private static final int GOLD = 0xFFC8902C, DARK = 0xE0101010, WHITE = 0xFFFFFFFF, GRAY = 0xFF8A8A8A;

	private final String nameA, nameB, kit;
	private final Stats stats;
	private final boolean aWon;
	private final int scoreA, scoreB;

	public BreakdownScreen(String nameA, String nameB, String kit, Stats stats, boolean aWon, int scoreA, int scoreB) {
		super(Component.literal("Round Breakdown"));
		this.nameA = nameA;
		this.nameB = nameB;
		this.kit = kit;
		this.stats = stats;
		this.aWon = aWon;
		this.scoreA = scoreA;
		this.scoreB = scoreB;
	}

	// --- version-sensitive drawing primitives -------------------------------
	private void fill(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int argb) {
		g.fill(x1, y1, x2, y2, argb);
	}

	private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		g.text(this.font, s, x, y, color, true);
	}

	private void centered(GuiGraphicsExtractor g, String s, int cx, int y, int color) {
		text(g, s, cx - this.font.width(s) / 2, y, color);
	}
	// -------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		fill(g, 0, 0, this.width, this.height, 0xB0000000);

		int panelW = Math.min(this.width - 40, 360);
		int cx = this.width / 2;
		int left = cx - panelW / 2, right = cx + panelW / 2;
		int top = Math.max(10, this.height / 2 - 100);

		// Header
		fill(g, left, top, right, top + 44, DARK);
		fill(g, left, top, right, top + 2, GOLD);
		centered(g, "ROUND BREAKDOWN", cx, top + 10, WHITE);
		centered(g, nameA + " vs " + nameB, cx, top + 26, GOLD);

		// Stats table
		int ty = top + 52;
		fill(g, left, ty, right, ty + 124, DARK);
		fill(g, left, ty, right, ty + 14, GOLD);
		centered(g, kit.toUpperCase(), cx, ty + 3, 0xFF000000);
		text(g, "Winner > " + (aWon ? nameA : nameB), right - 6 - this.font.width("Winner > " + (aWon ? nameA : nameB)), ty + 3, 0xFF000000);

		row(g, ty + 20, left, right, "Damage", f1(stats.a.damage), f1(stats.b.damage), stats.a.damage >= stats.b.damage);
		row(g, ty + 42, left, right, "Regen", f1(stats.a.regen), f1(stats.b.regen), stats.a.regen >= stats.b.regen);
		row(g, ty + 64, left, right, "Total Hits", "" + stats.a.hits, "" + stats.b.hits, stats.a.hits >= stats.b.hits);
		row(g, ty + 86, left, right, "Accuracy", f1(stats.a.accuracy()) + "%", f1(stats.b.accuracy()) + "%", stats.a.accuracy() >= stats.b.accuracy());
		row(g, ty + 108, left, right, "Critical Hits", "" + stats.a.crits, "" + stats.b.crits, stats.a.crits >= stats.b.crits);

		// Winner banner and match score
		int by = ty + 132;
		fill(g, left, by, right, by + 28, DARK);
		fill(g, left, by + 26, right, by + 28, GOLD);
		centered(g, "WINNER: " + (aWon ? nameA : nameB), cx - panelW / 4, by + 9, GOLD);
		centered(g, nameA + "  " + scoreA + " - " + scoreB + "  " + nameB, cx + panelW / 4, by + 9, WHITE);
		centered(g, "Match Score", cx + panelW / 4, by + 19, GRAY);
		centered(g, "Press ESC to close", cx, by + 36, GRAY);
	}

	private void row(GuiGraphicsExtractor g, int y, int left, int right, String label, String a, String b, boolean aLeads) {
		int cx = (left + right) / 2;
		fill(g, left, y + 17, right, y + 18, 0x40FFFFFF);
		centered(g, label, cx, y + 4, GOLD);
		text(g, a, left + 30, y + 4, aLeads ? WHITE : GRAY);
		text(g, b, right - 30 - this.font.width(b), y + 4, aLeads ? GRAY : WHITE);
	}

	private static String f1(double v) {
		return String.format(java.util.Locale.ROOT, "%.1f", v);
	}
}
