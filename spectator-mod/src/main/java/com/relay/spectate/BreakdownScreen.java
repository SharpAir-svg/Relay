package com.relay.spectate;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.PlayerFaceExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Post-round summary laid out like the PvP Champs card: winner on the left,
 * header / stat table / match score stacked on the right.
 *
 * All drawing goes through {@link #fill}, {@link #text}, {@link #centered} and
 * {@link #face}, so an API rename between Minecraft versions is a one-place fix.
 */
public final class BreakdownScreen extends Screen {
	private static final int GOLD = 0xFFD9A441, GOLD_DK = 0xFFA8741A, PANEL = 0xF2101010;
	private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFF7C7C7C, BLACK = 0xFF000000;

	private final String nameA, nameB, kit;
	private final Stats stats;
	private final PlayerSkin skinA, skinB; // may be null if the skin was never seen
	private final boolean aWon;
	private final int scoreA, scoreB;

	public BreakdownScreen(String nameA, String nameB, String kit, Stats stats, PlayerSkin skinA, PlayerSkin skinB,
			boolean aWon, int scoreA, int scoreB) {
		super(Component.literal("Round Breakdown"));
		this.nameA = nameA;
		this.nameB = nameB;
		this.kit = kit;
		this.stats = stats;
		this.skinA = skinA;
		this.skinB = skinB;
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

	/** Draws the 8x8 face (with hat layer) scaled to size x size. Falls back to a gold square. */
	private void face(GuiGraphicsExtractor g, PlayerSkin skin, int x, int y, int size) {
		if (skin == null) {
			fill(g, x, y, x + size, y + size, GOLD_DK);
			return;
		}
		PlayerFaceExtractor.extractRenderState(g, skin, x, y, size);
	}
	// -------------------------------------------------------------------------

	private void frame(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
		fill(g, x1 - 2, y1 - 2, x2 + 2, y2 + 2, GOLD);
		fill(g, x1, y1, x2, y2, PANEL);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		// Warm amber backdrop with a darker rim, like the spotlight in the card.
		fill(g, 0, 0, this.width, this.height, 0xFF4A2E0E);
		fill(g, this.width / 8, 0, this.width * 7 / 8, this.height, 0xFF6B4416);
		fill(g, this.width / 4, 0, this.width * 3 / 4, this.height, 0xFF8A5A1E);

		boolean aWins = aWon;
		String winnerName = aWins ? nameA : nameB;
		PlayerSkin winnerSkin = aWins ? skinA : skinB;

		// ---- left column: winner ------------------------------------------------
		int colW = Math.max(110, this.width / 4);
		int lx = Math.max(8, this.width / 24), lcx = lx + colW / 2;
		int plateW = Math.max(this.font.width(winnerName) + 24, 90);
		fill(g, lcx - plateW / 2, 14, lcx + plateW / 2, 36, 0xB0302010);
		centered(g, winnerName.toUpperCase(), lcx, 21, WHITE);

		int big = Math.min(colW - 10, this.height / 2 - 20);
		face(g, winnerSkin, lcx - big / 2, 46, big);

		int bannerTop = this.height - 56;
		fill(g, lx, bannerTop, lx + colW, bannerTop + 36, 0xFFF2C31E);
		fill(g, lx, bannerTop, lx + colW / 5, bannerTop + 36, 0xFFFFF2B0);
		centered(g, "WINNER", lcx + colW / 10, bannerTop + 14, GOLD_DK);

		// ---- right column: header, table, score -----------------------------------
		int rx1 = this.width * 2 / 5, rx2 = this.width - Math.max(8, this.width / 24);
		int cx = (rx1 + rx2) / 2;
		int top = 12;

		frame(g, rx1, top, rx2, top + 48);
		centered(g, "ROUND BREAKDOWN", cx, top + 12, WHITE);
		centered(g, nameA + " vs " + nameB, cx, top + 30, GOLD);

		int ty = top + 62, rowH = 24, rows = 5;
		int tbottom = ty + 20 + rows * rowH;
		frame(g, rx1, ty, rx2, tbottom);
		fill(g, rx1, ty + 18, rx2, ty + 22, GOLD); // gold rule under the title row
		centered(g, kit.toUpperCase(), cx, ty + 5, WHITE);
		String rw = "Round Winner >";
		text(g, rw, rx2 - 8 - this.font.width(rw), ty + 5, GOLD);

		int ry = ty + 22;
		row(g, ry, rx1, rx2, "Damage", f1(stats.a.damage), f1(stats.b.damage), stats.a.damage, stats.b.damage);
		row(g, ry + rowH, rx1, rx2, "Regen", f1(stats.a.regen), f1(stats.b.regen), stats.a.regen, stats.b.regen);
		row(g, ry + 2 * rowH, rx1, rx2, "Total Hits", "" + stats.a.hits, "" + stats.b.hits, stats.a.hits, stats.b.hits);
		row(g, ry + 3 * rowH, rx1, rx2, "Accuracy", f1(stats.a.accuracy()) + "%", f1(stats.b.accuracy()) + "%",
			stats.a.accuracy(), stats.b.accuracy());
		row(g, ry + 4 * rowH, rx1, rx2, "Critical Hits", "" + stats.a.crits, "" + stats.b.crits, stats.a.crits, stats.b.crits);

		// Match score: head, diamonds, centre badge, diamonds, head.
		int sy = tbottom + 12, sh = 40, head = 28;
		frame(g, rx1, sy, rx2, sy + sh);
		fill(g, rx1 - 2, sy + sh, rx2 + 2, sy + sh + 4, GOLD);
		face(g, skinA, rx1 + 8, sy + 6, head);
		face(g, skinB, rx2 - 8 - head, sy + 6, head);
		int slots = Math.max(2, Math.max(scoreA, scoreB));
		diamonds(g, cx - 32 - slots * 12, sy + 15, slots, scoreA, false);
		diamonds(g, cx + 32, sy + 15, slots, scoreB, true);
		centered(g, "PVP CHAMPS", cx, sy + 10, GOLD);
		centered(g, "Match Score", cx, sy + sh + 8, GOLD);

		centered(g, "Press ESC to close", cx, this.height - 12, GRAY);
	}

	/** Filled diamond per round won, hollow for the rest; B's row fills from the left like A's mirrors. */
	private void diamonds(GuiGraphicsExtractor g, int x, int y, int slots, int won, boolean mirrored) {
		for (int i = 0; i < slots; i++) {
			int idx = mirrored ? i : slots - 1 - i; // A fills toward the centre
			boolean filled = mirrored ? i < won : (slots - 1 - i) < won;
			text(g, filled ? "◆" : "◇", x + i * 12, y, filled ? WHITE : GRAY);
		}
	}

	/** Leader of each stat is bright, the other is dimmed (as in the card). */
	private void row(GuiGraphicsExtractor g, int y, int x1, int x2, String label, String a, String b, double va, double vb) {
		int cx = (x1 + x2) / 2;
		fill(g, x1, y + 23, x2, y + 24, 0x30FFFFFF);
		centered(g, label, cx, y + 8, GOLD);
		boolean aLeads = va >= vb;
		text(g, a, cx - 70 - this.font.width(a), y + 8, aLeads ? WHITE : GRAY);
		text(g, b, cx + 70, y + 8, aLeads ? GRAY : WHITE);
	}

	private static String f1(double v) {
		return String.format(java.util.Locale.ROOT, "%.1f", v);
	}
}
