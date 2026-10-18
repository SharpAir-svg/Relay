package com.relay.spectate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Second broadcast bar under the top bar: tracked item counts, armour durability per piece and the
 * total durability lead, mirrored around a centre label. Mirrors the layout of the PvP Champs bar.
 */
public final class InventoryBar implements HudElement {
	private static final int GOLD = 0xFFD9A441, PANEL = 0xE8101010;
	private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFF8A8A8A, RED = 0xFFFF5555, GREEN = 0xFF55FF55;
	private static final float ICON = 13f / 16f, TEXT = 0.85f;
	private static final int BAR_W = 660, H = 22;

	private final Map<String, ItemStack> iconCache = new HashMap<>();

	/** One icon plus one value. */
	private record Cell(ItemStack icon, String text, int color) {}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker tracker) {
		if (!RelayClient.hudVisible()) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int sw = mc.getWindow().getGuiScaledWidth();
		float k = Math.min(1f, (sw - 16f) / BAR_W);
		int W = Math.round(sw / k);
		int x0 = (W - BAR_W) / 2, y0 = 43, cx = W / 2;

		g.pose().pushMatrix();
		g.pose().scale(k, k);

		g.fill(x0 - 2, y0 - 2, x0 + BAR_W + 2, y0 + H + 2, GOLD);
		g.fill(x0, y0, x0 + BAR_W, y0 + H, PANEL);

		String label = "INVENTORY";
		float ls = 1.1f;
		int lw = Math.round(font.width(label) * ls);
		text(g, font, label, cx - lw / 2f, y0 + (H - 9 * ls) / 2f, ls, GOLD);

		ItemStack[] armA = RelayClient.armorOf(RelayClient.nameA()), armB = RelayClient.armorOf(RelayClient.nameB());
		int remA = total(armA), remB = total(armB);
		side(g, font, true, cx - lw / 2 - 8, y0, RelayClient.countsOf(RelayClient.nameA()), armA, remA - remB);
		side(g, font, false, cx + lw / 2 + 8, y0, RelayClient.countsOf(RelayClient.nameB()), armB, remB - remA);

		String kit = RelayClient.kitLabel();
		float kw = font.width(kit) * 0.8f;
		g.fill(Math.round(cx - kw / 2 - 5), y0 + H + 2, Math.round(cx + kw / 2 + 5), y0 + H + 13, PANEL);
		text(g, font, kit, cx - kw / 2f, y0 + H + 4, 0.8f, GOLD);
		g.pose().popMatrix();
	}

	/**
	 * Cells run outward from the centre: helmet, chestplate, leggings, boots, durability lead, items.
	 * The left side puts each number before its icon, the right side after it.
	 */
	private void side(GuiGraphicsExtractor g, Font font, boolean left, int start, int y0, Map<String, Integer> counts,
			ItemStack[] armor, int diff) {
		List<Cell> cells = new ArrayList<>();
		for (int slot = 0; slot < 4; slot++) { // armor[] is head, chest, legs, feet
			ItemStack s = armor[slot];
			cells.add(new Cell(s, s.isEmpty() ? "--" : "" + remaining(s), s.isEmpty() ? GRAY : WHITE));
		}
		cells.add(new Cell(ItemStack.EMPTY, (diff > 0 ? "+" : "") + diff, diff > 0 ? GREEN : diff < 0 ? RED : GRAY));
		if (counts != null) {
			// First tracked item sits at the outer edge, the last next to the durability lead.
			List<String> keys = RelayClient.trackedKeys();
			for (int i = keys.size() - 1; i >= 0; i--) {
				cells.add(new Cell(icon(keys.get(i)), "" + counts.getOrDefault(keys.get(i), 0), WHITE));
			}
		}

		int cursor = start;
		int iconPx = Math.round(16 * ICON);
		for (Cell c : cells) {
			int tw = Math.round(font.width(c.text) * TEXT);
			int w = tw + (c.icon.isEmpty() ? 0 : iconPx + 2) + 7;
			int cellX = left ? cursor - w : cursor;
			int iconY = y0 + (H - iconPx) / 2;
			float ty = y0 + (H - 9 * TEXT) / 2f;
			int ix, tx;
			if (c.icon.isEmpty()) { tx = cellX; ix = 0; }
			else if (left) { tx = cellX; ix = cellX + tw + 2; }
			else { ix = cellX; tx = cellX + iconPx + 2; }
			text(g, font, c.text, tx, ty, TEXT, c.color);
			if (!c.icon.isEmpty()) {
				g.pose().pushMatrix();
				g.pose().translate(ix, iconY);
				g.pose().scale(ICON, ICON);
				g.item(c.icon, 0, 0);
				g.pose().popMatrix();
			}
			cursor = left ? cursor - w : cursor + w;
		}
	}

	private ItemStack icon(String key) {
		return iconCache.computeIfAbsent(key, k -> {
			String path = k.startsWith("potion_") ? "splash_potion" : k;
			var item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path));
			return new ItemStack(item);
		});
	}

	static int remaining(ItemStack s) {
		return s.isDamageableItem() ? s.getMaxDamage() - s.getDamageValue() : 0;
	}

	private static int total(ItemStack[] armor) {
		int t = 0;
		for (ItemStack s : armor) t += remaining(s);
		return t;
	}

	private static void text(GuiGraphicsExtractor g, Font f, String s, float x, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(f, s, 0, 0, color, true);
		g.pose().popMatrix();
	}
}
