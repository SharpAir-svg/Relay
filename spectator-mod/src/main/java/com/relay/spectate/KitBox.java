package com.relay.spectate;

import java.util.HashMap;
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
 * Top-left box with the kit's icon, its name and who picked it, left of the top bar like the broadcast layout.
 * Hidden until a kit is known (detected or set with /spectate kit) and when the screen is too narrow for it.
 */
public final class KitBox implements HudElement {
	private static final int GOLD = 0xFFD9A441, PANEL = 0xE8101010, WHITE = 0xFFFFFFFF, GRAY = 0xFF9A9A9A;

	private final Map<String, ItemStack> icons = new HashMap<>();

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker tracker) {
		if (!RelayClient.hudVisible()) return;
		String name = RelayClient.kitLabel();
		String iconPath = RelayClient.kitIcon();
		if (iconPath == null && RelayClient.manualKitSet() == false) return; // no kit yet
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int W = mc.getWindow().getGuiScaledWidth();

		int barLeft = (W - TopBar.barWidth(W)) / 2;
		int x1 = 4, x2 = barLeft - 5, y1 = 3, h = 34;
		int w = x2 - x1;
		if (w < 70) return; // no room beside the bar

		g.fill(x1 - 2, y1 - 2, x2 + 2, y1 + h + 2, GOLD);
		g.fill(x1, y1, x2, y1 + h, PANEL);

		int textX = x1 + 6;
		if (iconPath != null) {
			g.pose().pushMatrix();
			g.pose().translate(x1 + 5, y1 + 4);
			g.pose().scale(1.5f, 1.5f); // a 24px icon
			g.item(icon(iconPath), 0, 0);
			g.pose().popMatrix();
			textX = x1 + 5 + 24 + 5;
		}
		float avail = x2 - 4 - textX;
		String label = name.toUpperCase();
		float scale = Math.min(1.0f, avail / Math.max(1, font.width(label)));
		text(g, font, label, textX, y1 + 8, scale, WHITE);
		String pick = RelayClient.pickName();
		if (pick != null) text(g, font, pick + " Pick", textX, y1 + 8 + 9 * scale + 3, Math.min(0.75f, avail / Math.max(1, font.width(pick + " Pick"))), GRAY);
	}

	private ItemStack icon(String path) {
		return icons.computeIfAbsent(path, p -> new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(p))));
	}

	private static void text(GuiGraphicsExtractor g, Font f, String s, float x, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(f, s, 0, 0, color, true);
		g.pose().popMatrix();
	}
}
