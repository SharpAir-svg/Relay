package com.relay.spectate;

import java.util.List;
import java.util.Map;

/** Works out which tournament kit is being played from what the fighters carry and wear. */
public enum Kit {
	NONE("Duel", List.of()),
	MACE("Mace", List.of("totem_of_undying", "wind_charge", "ender_pearl")),
	SPEAR_MACE("Spear & Mace", List.of("totem_of_undying", "wind_charge", "ender_pearl")),
	CART("Cart", List.of("experience_bottle", "totem_of_undying", "ender_pearl")),
	NETHERITE_OP("Netherite OP", List.of("potion_healing", "experience_bottle", "totem_of_undying", "potion_other")),
	DIAMOND_SMP("Diamond SMP", List.of("experience_bottle", "cobweb", "golden_apple")),
	NETHERITE_SMP("Netherite SMP", List.of("experience_bottle", "cobweb", "golden_apple"));

	public final String label;
	/** Count keys shown on the inventory bar, in order (item paths, or potion_healing / potion_other). */
	public final List<String> tracked;

	Kit(String label, List<String> tracked) {
		this.label = label;
		this.tracked = tracked;
	}

	/**
	 * @param counts item counts for one fighter (null if the server mod isn't there)
	 * @param armor  "netherite", "diamond" or null: the material if all four pieces match
	 */
	public static Kit detect(Map<String, Integer> counts, String armor) {
		if (counts == null) return NONE;
		int spears = 0;
		for (var e : counts.entrySet()) if (e.getKey().endsWith("_spear")) spears += e.getValue();

		if (get(counts, "tnt_minecart") > 0) return CART;
		// Maces only appear in the mace kits, so any mace settles it, whatever else is (or isn't) carried.
		// This check also runs before Netherite OP, which a mace kit in full netherite would otherwise match.
		if (get(counts, "mace") > 0) return spears > 0 ? SPEAR_MACE : MACE;
		// Cobwebs and oak logs are what mark an SMP kit; no other kit carries them. Requiring every SMP item
		// at once broke as soon as one was placed or used up, and fell through to Netherite OP.
		boolean smpItems = get(counts, "cobweb") > 0 || get(counts, "oak_log") > 0;
		if (smpItems && "diamond".equals(armor)) return DIAMOND_SMP;
		if (smpItems && "netherite".equals(armor)) return NETHERITE_SMP;
		// OP is a potion kit: lots of potions and none of the SMP items.
		int potions = get(counts, "potion_healing") + get(counts, "potion_other");
		if ("netherite".equals(armor) && potions >= 12) return NETHERITE_OP;
		return NONE;
	}

	private static int get(Map<String, Integer> m, String k) {
		return m.getOrDefault(k, 0);
	}
}
