package com.relay.spectate;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
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
	private final LivingEntity winnerEntity; // rendered full-body; may be null or removed
	private final boolean aWon;
	private final int scoreA, scoreB;

	public BreakdownScreen(String nameA, String nameB, String kit, Stats stats, PlayerSkin skinA, PlayerSkin skinB,
			LivingEntity winnerEntity, boolean aWon, int scoreA, int scoreB) {
		super(Component.literal("Round Breakdown"));
		this.nameA = nameA;
		this.nameB = nameB;
		this.kit = kit;
		this.stats = stats;
		this.skinA = skinA;
		this.skinB = skinB;
		this.winnerEntity = winnerEntity;
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

	/** Text drawn at an arbitrary scale (the font itself only has one size). */
	private void bigText(GuiGraphicsExtractor g, String s, float x, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		text(g, s, 0, 0, color);
		g.pose().popMatrix();
	}

	private void bigCentered(GuiGraphicsExtractor g, String s, float cx, float y, float scale, int color) {
		bigText(g, s, cx - this.font.width(s) * scale / 2f, y, scale, color);
	}

	/** Largest scale up to {@code max} at which {@code s} fits in {@code width} px. */
	private float fit(String s, float max, float width) {
		return Math.min(max, width / Math.max(1, this.font.width(s)));
	}

	/** Full-body render of the winner; falls back to a large head if unavailable. */
	private void figure(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int mx, int my, PlayerSkin skin) {
		if (winnerEntity != null && !winnerEntity.isRemoved()) {
			int scale = Math.max(10, (y2 - y1) * 10 / 21); // ~1.9 blocks tall at 0.525 px per unit
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, scale, 0.0625f, mx, my, winnerEntity);
		} else {
			int big = Math.min(x2 - x1, y2 - y1);
			face(g, skin, (x1 + x2) / 2 - big / 2, y1, big);
		}
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

		// Lay out in a design space of at least 240 tall, scaled down on big GUI scales.
		final float designH = 240f;
		float k = Math.min(1f, this.height / designH);
		int W = Math.round(this.width / k), H = Math.round(this.height / k);
		int mx = Math.round(mouseX / k), my = Math.round(mouseY / k);
		g.pose().pushMatrix();
		g.pose().scale(k, k);
		drawCard(g, W, H, mx, my);
		g.pose().popMatrix();
	}

	private void drawCard(GuiGraphicsExtractor g, int W, int H, int mx, int my) {
		String winnerName = aWon ? nameA : nameB;
		PlayerSkin winnerSkin = aWon ? skinA : skinB;

		// ---- left column: winner ------------------------------------------------
		int colW = Math.max(110, W / 4);
		int lx = Math.max(8, W / 24), lcx = lx + colW / 2;
		int plateW = Math.max((int) (this.font.width(winnerName) * 1.5f) + 24, 90);
		fill(g, lcx - plateW / 2, 8, lcx + plateW / 2, 32, 0xB0302010);
		bigCentered(g, winnerName.toUpperCase(), lcx, 14, 1.5f, WHITE);

		int bannerTop = H - 52, bannerH = 36;
		figure(g, lx, 38, lx + colW, bannerTop - 4, mx, my, winnerSkin);
		fill(g, lx, bannerTop, lx + colW, bannerTop + bannerH, 0xFFF2C31E);
		fill(g, lx, bannerTop, lx + colW / 5, bannerTop + bannerH, 0xFFFFF2B0);
		float ws = fit("WINNER", 2.5f, colW * 0.72f);
		bigCentered(g, "WINNER", lcx + colW / 10f, bannerTop + (bannerH - 9 * ws) / 2f, ws, GOLD_DK);

		// ---- right column: header, table, score -----------------------------------
		int rx1 = W * 2 / 5, rx2 = W - Math.max(8, W / 24);
		int cx = (rx1 + rx2) / 2;
		int top = 6;

		int headH = 40;
		frame(g, rx1, top, rx2, top + headH);
		bigCentered(g, "ROUND BREAKDOWN", cx, top + 5, 2.0f, WHITE);
		centered(g, nameA + " vs " + nameB, cx, top + 27, GOLD);

		int ty = top + headH + 8, titleH = 22, rowH = 21, rows = 5;
		int tbottom = ty + titleH + rows * rowH;
		frame(g, rx1, ty, rx2, tbottom);
		fill(g, rx1, ty + titleH - 4, rx2, ty + titleH, GOLD); // gold rule under the title row
		String rw = "Round Winner >";
		int rwW = this.font.width(rw);
		text(g, rw, rx2 - 8 - rwW, ty + 7, GOLD);
		float availW = (rx2 - 8 - rwW - 8) - (rx1 + 8);
		float ks = fit(kit.toUpperCase(), 1.5f, availW);
		bigCentered(g, kit.toUpperCase(), rx1 + 8 + availW / 2f, ty + (titleH - 4 - 9 * ks) / 2f, ks, WHITE);

		int ry = ty + titleH;
		row(g, ry, rx1, rx2, rowH, "Damage", f1(stats.a.damage), f1(stats.b.damage), stats.a.damage, stats.b.damage);
		row(g, ry + rowH, rx1, rx2, rowH, "Regen", f1(stats.a.regen), f1(stats.b.regen), stats.a.regen, stats.b.regen);
		row(g, ry + 2 * rowH, rx1, rx2, rowH, "Total Hits", "" + stats.a.hits, "" + stats.b.hits, stats.a.hits, stats.b.hits);
		row(g, ry + 3 * rowH, rx1, rx2, rowH, "Accuracy", f1(stats.a.accuracy()) + "%", f1(stats.b.accuracy()) + "%",
			stats.a.accuracy(), stats.b.accuracy());
		row(g, ry + 4 * rowH, rx1, rx2, rowH, "Critical Hits", "" + stats.a.crits, "" + stats.b.crits, stats.a.crits, stats.b.crits);

		// Match score: head, diamonds, centre badge, diamonds, head.
		int sy = tbottom + 8, sh = 36, head = 26;
		frame(g, rx1, sy, rx2, sy + sh);
		fill(g, rx1 - 2, sy + sh, rx2 + 2, sy + sh + 4, GOLD);
		face(g, skinA, rx1 + 6, sy + 5, head);
		face(g, skinB, rx2 - 6 - head, sy + 5, head);
		int slots = Math.max(2, Math.max(scoreA, scoreB));
		diamonds(g, cx - 40 - slots * 14, sy + 14, slots, scoreA, false);
		diamonds(g, cx + 40, sy + 14, slots, scoreB, true);
		bigCentered(g, "PVP CHAMPS", cx, sy + 9, 1.2f, GOLD);
		centered(g, "Match Score", cx, sy + sh + 7, GOLD);

		centered(g, "Press ESC to close", lcx, H - 12, GRAY);
	}

	/** Filled diamond per round won, hollow for the rest; B's row fills from the left like A's mirrors. */
	private void diamonds(GuiGraphicsExtractor g, int x, int y, int slots, int won, boolean mirrored) {
		for (int i = 0; i < slots; i++) {
			boolean filled = mirrored ? i < won : (slots - 1 - i) < won;
			text(g, filled ? "◆" : "◇", x + i * 12, y, filled ? WHITE : GRAY);
		}
	}

	/** Leader of each stat is bright, the other is dimmed (as in the card). */
	private void row(GuiGraphicsExtractor g, int y, int x1, int x2, int rowH, String label, String a, String b, double va, double vb) {
		int cx = (x1 + x2) / 2;
		fill(g, x1, y + rowH - 1, x2, y + rowH, 0x30FFFFFF);
		centered(g, label, cx, y + rowH / 2 - 4, GOLD);
		boolean aLeads = va >= vb;
		float sc = 1.5f;
		bigText(g, a, cx - 62 - this.font.width(a) * sc, y + (rowH - 9 * sc) / 2f, sc, aLeads ? WHITE : GRAY);
		bigText(g, b, cx + 62, y + (rowH - 9 * sc) / 2f, sc, aLeads ? GRAY : WHITE);
	}

	private static String f1(double v) {
		return String.format(java.util.Locale.ROOT, "%.1f", v);
	}
}
