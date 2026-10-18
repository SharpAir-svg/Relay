package com.relay.spectate;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

/** Server side: totals every item a player carries, wearing included. */
public final class InventoryScan {
	private InventoryScan() {}

	public static Map<String, Integer> count(Player p) {
		Map<String, Integer> out = new HashMap<>();
		// Worn items may also appear in the inventory list; count each stack object once.
		Map<ItemStack, Boolean> seen = new IdentityHashMap<>();
		for (EquipmentSlot slot : EquipmentSlot.values()) add(out, seen, p.getItemBySlot(slot));
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) add(out, seen, inv.getItem(i));
		return out;
	}

	private static void add(Map<String, Integer> out, Map<ItemStack, Boolean> seen, ItemStack s) {
		if (s.isEmpty() || seen.put(s, true) != null) return;
		String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		String key = path;
		if (path.equals("potion") || path.equals("splash_potion") || path.equals("lingering_potion")) {
			key = isHealing(s) ? "potion_healing" : "potion_other";
		}
		out.merge(key, s.getCount(), Integer::sum);
	}

	private static boolean isHealing(ItemStack s) {
		PotionContents pc = s.get(DataComponents.POTION_CONTENTS);
		if (pc == null) return false;
		for (MobEffectInstance e : pc.getAllEffects()) {
			if (e.getEffect().value() == MobEffects.INSTANT_HEALTH.value()) return true;
		}
		return false;
	}
}
