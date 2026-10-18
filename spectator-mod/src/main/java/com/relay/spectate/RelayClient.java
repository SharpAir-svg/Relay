package com.relay.spectate;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * /spectate <a> <b>  : auto-directed camera on a duel, follows ender pearls.
 * Totem pop or death : 20 s replay of the lead-up, then (death only) the breakdown.
 * /replay exit       : leave a replay early.
 */
public final class RelayClient implements ClientModInitializer {
	private enum Mode { LIVE, REPLAY }
	private enum Cause { TOTEM, DEATH }

	private static final int TOTEM_EVENT = 35;
	private static final int DEATH_REARM_TICKS = 200;
	private static final int DEATH_EVENT = 3;
	private static final int MISSING_TICKS = 20; // a fighter gone this long (teleported, respawned) counts as out

	// session
	private static boolean active;
	private static String nameA = "", nameB = "", manualKit;
	private static Kit detectedKit = Kit.NONE;
	private static final Map<String, Map<String, Integer>> summaries = new ConcurrentHashMap<>();
	private static int netTicks;
	private static Mode mode = Mode.LIVE;
	private static final Recorder recorder = new Recorder();
	private static final CameraDirector director = new CameraDirector();
	private static Stats stats = new Stats();
	static int scoreA, scoreB, seedA, seedB;
	private static final boolean DEMO = System.getProperty("relay.demo") != null; // dev-only screenshot mode
	private static int demoTicks;
	private static int deathCooldown;
	private static Boolean pendingTotemIsA;
	private static Boolean pendingDeathIsA; // set by the death entity event
	private static boolean seenBoth;
	private static boolean replaysEnabled; // off by default: replays interrupted the live view more than they helped
	private static int missingTicks;
	private static boolean tickedSinceFrame; // lets the director run its slow logic once per game tick
	private static long lastFrameNs;
	static PlayerSkin skinA, skinB; // cached while the players are loaded

	// replay
	private static List<Recorder.Tick> replayFrames = List.of();
	private static int replayIndex;
	private static Cause replayCause;
	private static boolean replayLoserIsA;
	private static RemotePlayer ghostA, ghostB;
	private static final List<Entity> hidden = new ArrayList<>();
	private static final List<Boolean> hiddenWas = new ArrayList<>();

	// last finished round, for /breakdown
	private static BreakdownScreen lastBreakdown;

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
			dispatcher.register(ClientCommands.literal("spectate")
				.then(ClientCommands.literal("stop").executes(c -> { stop(c.getSource()); return 1; }))
				.then(ClientCommands.literal("seeds")
					.then(ClientCommands.argument("a", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
						.then(ClientCommands.argument("b", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
							.executes(c -> {
								seedA = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "a");
								seedB = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "b");
								c.getSource().sendFeedback(Component.literal("Seeds set to " + seedA + " / " + seedB));
								return 1;
							}))))
				.then(ClientCommands.literal("replay")
					.then(ClientCommands.literal("on").executes(c -> { replaysEnabled = true; c.getSource().sendFeedback(Component.literal("Replays on.")); return 1; }))
					.then(ClientCommands.literal("off").executes(c -> { replaysEnabled = false; c.getSource().sendFeedback(Component.literal("Replays off.")); return 1; })))
				.then(ClientCommands.literal("win")
					.then(ClientCommands.argument("player", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
						.executes(c -> { win(c.getSource(), StringArgumentType.getString(c, "player")); return 1; })))
				.then(ClientCommands.literal("kit")
					.then(ClientCommands.argument("name", StringArgumentType.greedyString())
						.executes(c -> {
							manualKit = StringArgumentType.getString(c, "name");
							c.getSource().sendFeedback(Component.literal("Kit label set to " + manualKit));
							return 1;
						})))
				.then(ClientCommands.argument("a", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
					.then(ClientCommands.argument("b", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
						.executes(c -> {
							start(c.getSource(), StringArgumentType.getString(c, "a"), StringArgumentType.getString(c, "b"));
							return 1;
						}))));
			dispatcher.register(ClientCommands.literal("replay")
				.then(ClientCommands.literal("exit").executes(c -> { exitReplay(); return 1; })));
			dispatcher.register(ClientCommands.literal("breakdown").executes(c -> {
				if (lastBreakdown == null) c.getSource().sendFeedback(Component.literal("No finished round yet."));
				else Minecraft.getInstance().schedule(() -> Minecraft.getInstance().setScreenAndShow(lastBreakdown));
				return 1;
			}));
		});
		ClientPlayNetworking.registerGlobalReceiver(RelayNet.Summary.TYPE, (payload, ctx) -> {
			if (DEMO) { System.out.println("[relay_spectator] demo: server answered for " + payload.name() + " " + payload.counts()); return; }
			summaries.put(payload.name().toLowerCase(Locale.ROOT), payload.counts());
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("relay_spectator", "top_bar"), new TopBar());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("relay_spectator", "inventory_bar"), new InventoryBar());
		ClientTickEvents.END_CLIENT_TICK.register(RelayClient::tick);
		LevelRenderEvents.START_MAIN.register(ctx -> frame(Minecraft.getInstance()));
	}

	// ------------------------------------------------------------------ HUD accessors

	static boolean hudVisible() { return active; }
	static String nameA() { return nameA; }
	static String nameB() { return nameB; }
	static PlayerSkin skinA() { return skinA; }
	static PlayerSkin skinB() { return skinB; }

	static String kitLabel() { return manualKit != null ? manualKit : detectedKit.label; }
	static java.util.List<String> trackedKeys() { return detectedKit.tracked; }

	static Map<String, Integer> countsOf(String name) {
		return summaries.get(name.toLowerCase(Locale.ROOT));
	}

	/** head, chest, legs, feet as the client sees them (equipment is synced, inventories are not). */
	static ItemStack[] armorOf(String name) {
		if (DEMO) return demoArmor(name.equals(nameA) ? new int[] { 302, 487, 450, 376 } : new int[] { 328, 513, 476, 402 });
		ItemStack[] out = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return out;
		Player p = find(mc.level, name);
		if (p != null) {
			out[0] = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
			out[1] = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
			out[2] = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
			out[3] = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
		}
		return out;
	}

	/** "netherite" / "diamond" if all four pieces are that material, else null. */
	private static String armorMaterial(ItemStack[] armor) {
		String mat = null;
		for (ItemStack s : armor) {
			if (s.isEmpty()) return null;
			String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
			String m = path.startsWith("netherite_") ? "netherite" : path.startsWith("diamond_") ? "diamond" : null;
			if (m == null || (mat != null && !mat.equals(m))) return null;
			mat = m;
		}
		return mat;
	}

	/** Every half second: ask the server for inventories and re-detect the kit (a detection sticks once made). */
	private static void netTick() {
		if (++netTicks % 10 != 0) return;
		if (ClientPlayNetworking.canSend(RelayNet.Request.TYPE)) ClientPlayNetworking.send(new RelayNet.Request(nameA, nameB));
		// Both fighters play the same kit, so judge it from their combined items and armour. A kit is kept for
		// the rest of the round: items get used up mid-fight, and re-judging then would flip it to another kit.
		if (detectedKit != Kit.NONE) return;
		Map<String, Integer> a = countsOf(nameA), b = countsOf(nameB);
		Map<String, Integer> merged = null;
		if (a != null || b != null) {
			merged = new java.util.HashMap<>();
			for (Map<String, Integer> side : java.util.Arrays.asList(a, b)) {
				if (side == null) continue;
				for (var e : side.entrySet()) merged.merge(e.getKey(), e.getValue(), Integer::sum);
			}
		}
		String armor = armorMaterial(armorOf(nameA));
		if (armor == null) armor = armorMaterial(armorOf(nameB));
		detectedKit = Kit.detect(merged, armor);
	}

	static float healthOf(String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return 0;
		Player p = find(mc.level, name);
		if (p != null) return p.getHealth();
		return DEMO ? 16f : 0f;
	}

	/** Dev only: netherite armour worn down to the given remaining durability (head, chest, legs, feet). */
	private static ItemStack[] demoArmor(int[] remaining) {
		Item[] items = { Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS };
		ItemStack[] out = new ItemStack[4];
		for (int i = 0; i < 4; i++) {
			out[i] = new ItemStack(items[i]);
			out[i].setDamageValue(out[i].getMaxDamage() - remaining[i]);
		}
		return out;
	}

	/** Dev only (-Drelay.demo): fake a round so the HUD and breakdown can be screenshotted without a match. */
	private static void demoTick(Minecraft mc) {
		if (mc.player == null || mc.level == null) { demoTicks = 0; return; }
		demoTicks++;
		if (demoTicks % 100 == 50 && ClientPlayNetworking.canSend(RelayNet.Request.TYPE)) {
			ClientPlayNetworking.send(new RelayNet.Request(mc.player.getName().getString(), "x_Kyran_x")); // exercises the server round trip
		}
		if (demoTicks == 40) {
			nameA = mc.player.getName().getString();
			nameB = "x_Kyran_x";
			detectedKit = Kit.SPEAR_MACE;
			summaries.put(nameA.toLowerCase(Locale.ROOT), Map.of("totem_of_undying", 0, "wind_charge", 51, "ender_pearl", 64));
			summaries.put(nameB.toLowerCase(Locale.ROOT), Map.of("totem_of_undying", 1, "wind_charge", 51, "ender_pearl", 64));
			seedA = 8;
			seedB = 12;
			scoreA = 1;
			scoreB = 0;
			skinA = mc.player.getSkin();
			skinB = skinA;
			active = true;
		}
		if (demoTicks == Integer.getInteger("relay.demo.breakdown", 400)) {
			Stats st = new Stats();
			st.a.damage = 151.7; st.b.damage = 109.6;
			st.a.regen = 136.9; st.b.regen = 220.8;
			st.a.hits = 89; st.b.hits = 76;
			st.a.swings = 754; st.b.swings = 667;
			st.a.crits = 23; st.b.crits = 61;
			lastBreakdown = new BreakdownScreen(nameA, nameB, kitLabel(), st, skinA, skinB, mc.player, true, 1, 0);
			mc.setScreenAndShow(lastBreakdown);
		}
	}

	// ------------------------------------------------------------------ commands

	private static void start(FabricClientCommandSource src, String a, String b) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		if (!mc.player.isSpectator()) {
			src.sendFeedback(Component.literal("You must be in spectator mode (/gamemode spectator) to use /spectate."));
			return;
		}
		if (a.equalsIgnoreCase(b)) {
			src.sendFeedback(Component.literal("Pick two different players."));
			return;
		}
		cleanupReplay();
		summaries.clear();
		detectedKit = Kit.NONE;
		manualKit = null;
		nameA = a;
		nameB = b;
		stats = new Stats();
		recorder.clear();
		director.reset();
		scoreA = scoreB = 0;
		skinA = skinB = null;
		deathCooldown = 0;
		pendingTotemIsA = null;
		pendingDeathIsA = null;
		seenBoth = false;
		missingTicks = 0;
		mode = Mode.LIVE;
		active = true;
		src.sendFeedback(Component.literal("Spectating " + a + " vs " + b + ". /spectate stop to end."));
	}

	private static void stop(FabricClientCommandSource src) {
		cleanupReplay();
		active = false;
		src.sendFeedback(Component.literal("Stopped spectating."));
	}

	/** Manual fallback: declare a winner and run the replay and breakdown, whatever the server did. */
	private static void win(FabricClientCommandSource src, String winner) {
		Minecraft mc = Minecraft.getInstance();
		if (!active || mc.player == null) { src.sendFeedback(Component.literal("Start /spectate first.")); return; }
		if (mode != Mode.LIVE) { src.sendFeedback(Component.literal("Finish or /replay exit the current replay first.")); return; }
		boolean loserIsA;
		if (winner.equalsIgnoreCase(nameA)) loserIsA = false;
		else if (winner.equalsIgnoreCase(nameB)) loserIsA = true;
		else { src.sendFeedback(Component.literal(winner + " isn't one of the two fighters.")); return; }
		endRound(mc, loserIsA, "was beaten");
	}

	private static void exitReplay() {
		if (active && mode == Mode.REPLAY) finishReplay();
	}

	// ------------------------------------------------------------------ events

	/** Called from the packet mixin on the main thread. */
	public static void onEntityEvent(Entity entity, int id) {
		if (!active || mode != Mode.LIVE || !(entity instanceof Player p)) return;
		if (id != TOTEM_EVENT && id != DEATH_EVENT) return;
		String n = p.getName().getString();
		boolean isA;
		if (n.equalsIgnoreCase(nameA)) isA = true;
		else if (n.equalsIgnoreCase(nameB)) isA = false;
		else return;
		if (id == DEATH_EVENT) { pendingDeathIsA = isA; return; }
		pendingTotemIsA = isA;
		stats.totemPopped(isA);
	}

	// ------------------------------------------------------------------ tick

	private static Player find(net.minecraft.world.level.Level level, String name) {
		for (Player p : level.players()) if (p.getName().getString().equalsIgnoreCase(name)) return p;
		return null;
	}

	private static void tick(Minecraft mc) {
		if (DEMO) demoTick(mc);
		if (!active) return;
		LocalPlayer me = mc.player;
		if (me == null || mc.level == null) { active = false; cleanupReplay(); return; }
		if (!me.isSpectator()) return; // never drag a non-spectator around
		tickedSinceFrame = true;
		netTick();

		if (mode == Mode.REPLAY) { tickReplay(mc, me); return; }

		Player pa = find(mc.level, nameA), pb = find(mc.level, nameB);
		if (deathCooldown > 0) deathCooldown--;

		if (pa != null && pb != null) {
			seenBoth = true;
			missingTicks = 0;
			stats.tick(pa, pb);
			if (pa instanceof AbstractClientPlayer ca) skinA = ca.getSkin();
			if (pb instanceof AbstractClientPlayer cb) skinB = cb.getSkin();
			recorder.record(Frame.of(pa), Frame.of(pb));
		}

		// A round ends when a fighter dies, is put in spectator, or vanishes (teleported/respawned).
		// Servers differ in which of those they do, so any of them counts.
		Boolean loserIsA = null;
		String why = "";
		if (deathCooldown == 0 && seenBoth) {
			if (pendingDeathIsA != null) { loserIsA = pendingDeathIsA; why = "died"; }
			else if (pa != null && isOut(pa)) { loserIsA = true; why = "is out"; }
			else if (pb != null && isOut(pb)) { loserIsA = false; why = "is out"; }
			else if ((pa == null) != (pb == null)) {
				if (++missingTicks >= MISSING_TICKS) { loserIsA = pa == null; why = "left the fight"; }
			}
		}
		pendingDeathIsA = null;
		if (loserIsA != null) {
			endRound(mc, loserIsA, why);
			return;
		}

		if (pa == null || pb == null) return;
		if (pendingTotemIsA != null) {
			boolean isA = pendingTotemIsA;
			pendingTotemIsA = null;
			startReplay(mc, Cause.TOTEM, isA);
		}
	}

	private static boolean isOut(Player p) {
		return p.getHealth() <= 0 || p.isDeadOrDying() || p.isSpectator();
	}

	/** Replay the lead-up to the knockout, then the breakdown. */
	private static void endRound(Minecraft mc, boolean loserIsA, String why) {
		deathCooldown = DEATH_REARM_TICKS;
		detectedKit = Kit.NONE; // next round may be a different kit
		manualKit = null;
		missingTicks = 0;
		seenBoth = false;
		pendingTotemIsA = null;
		mc.player.sendSystemMessage(Component.literal((loserIsA ? nameA : nameB) + " " + why + " - round over."));
		startReplay(mc, Cause.DEATH, loserIsA);
	}

	/** Runs every rendered frame so the camera moves smoothly instead of in 20 Hz steps. */
	private static void frame(Minecraft mc) {
		long now = System.nanoTime();
		double dt = lastFrameNs == 0 ? 0.016 : Math.min(0.1, (now - lastFrameNs) / 1.0E9);
		lastFrameNs = now;
		LocalPlayer me = mc.player;
		if (!active || me == null || mc.level == null || !me.isSpectator()) return;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		boolean tickStep = tickedSinceFrame;
		tickedSinceFrame = false;

		Vec3 a, b, pearlPos = null, pearlVel = null;
		if (mode == Mode.REPLAY) {
			if (replayFrames.isEmpty() || replayIndex < 1) return;
			// Ghosts interpolate from the previous applied frame to the latest one.
			Recorder.Tick cur = replayFrames.get(Math.min(replayIndex, replayFrames.size()) - 1);
			Recorder.Tick prev = replayFrames.get(Math.max(0, Math.min(replayIndex, replayFrames.size()) - 2));
			a = prev.a().pos().lerp(cur.a().pos(), pt);
			b = prev.b().pos().lerp(cur.b().pos(), pt);
		} else {
			Player pa = find(mc.level, nameA), pb = find(mc.level, nameB);
			if (pa == null || pb == null) return;
			a = pa.getPosition(pt);
			b = pb.getPosition(pt);
			// Follow the nearest ender pearl in flight.
			Vec3 mid = a.add(b).scale(0.5);
			double best = 80 * 80;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e.getType() != EntityTypes.ENDER_PEARL || e.isRemoved()) continue;
				double d = e.position().distanceToSqr(mid);
				if (d < best) { best = d; pearlPos = e.getPosition(pt); pearlVel = e.getDeltaMovement(); }
			}
		}
		applyCamera(me, director.update(mc.level, me, a, b, pearlPos, pearlVel, dt, tickStep));
	}

	private static void applyCamera(LocalPlayer me, CameraDirector.Pose pose) {
		me.setPos(pose.eye().x, pose.eye().y - me.getEyeHeight(), pose.eye().z);
		me.setDeltaMovement(Vec3.ZERO);
		me.setYRot(pose.yaw());
		me.setXRot(pose.pitch());
		me.setYHeadRot(pose.yaw());
		me.setOldPosAndRot();
	}

	// ------------------------------------------------------------------ replay

	private static void startReplay(Minecraft mc, Cause cause, boolean loserIsA) {
		List<Recorder.Tick> frames = recorder.snapshot();
		if (!replaysEnabled || frames.size() < 10) {
			if (cause == Cause.DEATH) showBreakdown(mc, loserIsA);
			return;
		}
		try {
			ghostA = spawnGhost(mc, nameA, frames.get(0).a().pos());
			ghostB = spawnGhost(mc, nameB, frames.get(0).b().pos());
		} catch (Throwable t) {
			System.err.println("[relay_spectator] could not create replay ghosts: " + t);
			cleanupReplay();
			if (cause == Cause.DEATH) showBreakdown(mc, loserIsA);
			return;
		}
		for (String n : List.of(nameA, nameB)) {
			Player real = find(mc.level, n);
			if (real == null) continue;
			hidden.add(real);
			hiddenWas.add(real.isInvisible());
			real.setInvisible(true);
		}
		replayFrames = frames;
		replayIndex = 0;
		replayCause = cause;
		replayLoserIsA = loserIsA;
		mode = Mode.REPLAY;
		director.reset();
		mc.player.sendSystemMessage(Component.literal(
			"Replay: last " + frames.size() / 20 + "s before the " + (cause == Cause.TOTEM ? "totem pop" : "knockout") + ". /replay exit to skip."));
	}

	private static RemotePlayer spawnGhost(Minecraft mc, String name, Vec3 pos) {
		// Fresh UUID: the level refuses a second entity with the real player's UUID.
		// Side effect: ghosts use the default skin and carry a name tag instead.
		RemotePlayer g = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), name));
		g.setId(-9000 - (int) (Math.random() * 1000));
		g.setPos(pos);
		g.setNoGravity(true);
		g.noPhysics = true;
		g.setCustomName(Component.literal(name));
		g.setCustomNameVisible(true);
		mc.level.addEntity(g);
		return g;
	}

	private static void tickReplay(Minecraft mc, LocalPlayer me) {
		if (replayIndex >= replayFrames.size()) { finishReplay(); return; }
		Recorder.Tick t = replayFrames.get(replayIndex);
		boolean prevA = replayIndex > 0 && replayFrames.get(replayIndex - 1).a().swinging();
		boolean prevB = replayIndex > 0 && replayFrames.get(replayIndex - 1).b().swinging();
		applyGhost(ghostA, t.a(), prevA);
		applyGhost(ghostB, t.b(), prevB);
		replayIndex++;
	}

	private static void applyGhost(RemotePlayer g, Frame f, boolean wasSwinging) {
		if (g == null) return;
		g.setPos(f.pos());
		g.setYRot(f.yRot());
		g.setXRot(f.xRot());
		g.setYHeadRot(f.headRot());
		g.setYBodyRot(f.yRot());
		g.setPose(f.pose());
		EquipmentSlot[] slots = EquipmentSlot.values();
		for (int i = 0; i < slots.length; i++) g.setItemSlot(slots[i], f.gear()[i].copy());
		if (f.swinging() && !wasSwinging) g.swing(InteractionHand.MAIN_HAND);
	}

	private static void finishReplay() {
		Cause cause = replayCause;
		boolean loserIsA = replayLoserIsA;
		cleanupReplay();
		recorder.clear();
		director.reset();
		if (cause == Cause.DEATH) showBreakdown(Minecraft.getInstance(), loserIsA);
	}

	private static void showBreakdown(Minecraft mc, boolean loserIsA) {
		boolean aWon = !loserIsA;
		if (aWon) scoreA++; else scoreB++;
		lastBreakdown = new BreakdownScreen(nameA, nameB, kitLabel(), stats, skinA, skinB, mc.level == null ? null : find(mc.level, aWon ? nameA : nameB), aWon, scoreA, scoreB);
		stats = new Stats(); // fresh numbers for the next round
		mc.setScreenAndShow(lastBreakdown);
	}

	private static void cleanupReplay() {
		if (ghostA != null) ghostA.discard();
		if (ghostB != null) ghostB.discard();
		ghostA = ghostB = null;
		for (int i = 0; i < hidden.size(); i++) hidden.get(i).setInvisible(hiddenWas.get(i));
		hidden.clear();
		hiddenWas.clear();
		replayFrames = List.of();
		mode = Mode.LIVE;
	}
}
