package com.relay.spectate;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** One tick of recorded state for a single fighter. */
public record Frame(Vec3 pos, float yRot, float xRot, float headRot, Pose pose, boolean swinging, ItemStack[] gear) {

	public static Frame of(Player p) {
		EquipmentSlot[] slots = EquipmentSlot.values();
		ItemStack[] gear = new ItemStack[slots.length];
		for (int i = 0; i < slots.length; i++) gear[i] = p.getItemBySlot(slots[i]).copy();
		return new Frame(p.position(), p.getYRot(), p.getXRot(), p.getYHeadRot(), p.getPose(), p.swinging, gear);
	}
}
