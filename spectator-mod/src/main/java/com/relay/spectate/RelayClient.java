package com.relay.spectate;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
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

	// session
	private static boolean active;
	private static String nameA = "", nameB = "", kit = "Duel";
	private static Mode mode = Mode.LIVE;
	private static final Recorder recorder = new Recorder();
	private static final CameraDirector director = new CameraDirector();
	private static Stats stats = new Stats();
	private static int scoreA, scoreB;
	private static int deathCooldown;
	private static Boolean pendingTotemIsA;

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
			dispatcher.register(ClientCommandManager.literal("spectate")
				.then(ClientCommandManager.literal("stop").executes(c -> { stop(c.getSource()); return 1; }))
				.then(ClientCommandManager.literal("kit")
					.then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
						.executes(c -> {
							kit = StringArgumentType.getString(c, "name");
							c.getSource().sendFeedback(Component.literal("Kit label set to " + kit));
							return 1;
						})))
				.then(ClientCommandManager.argument("a", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
					.then(ClientCommandManager.argument("b", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
						.executes(c -> {
							start(c.getSource(), StringArgumentType.getString(c, "a"), StringArgumentType.getString(c, "b"));
							return 1;
						}))));
			dispatcher.register(ClientCommandManager.literal("replay")
				.then(ClientCommandManager.literal("exit").executes(c -> { exitReplay(); return 1; })));
			dispatcher.register(ClientCommandManager.literal("breakdown").executes(c -> {
				if (lastBreakdown == null) c.getSource().sendFeedback(Component.literal("No finished round yet."));
				else Minecraft.getInstance().schedule(() -> Minecraft.getInstance().setScreen(lastBreakdown));
				return 1;
			}));
		});
		ClientTickEvents.END_CLIENT_TICK.register(RelayClient::tick);
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
		nameA = a;
		nameB = b;
		stats = new Stats();
		recorder.clear();
		director.reset();
		scoreA = scoreB = 0;
		deathCooldown = 0;
		pendingTotemIsA = null;
		mode = Mode.LIVE;
		active = true;
		src.sendFeedback(Component.literal("Spectating " + a + " vs " + b + ". /spectate stop to end."));
	}

	private static void stop(FabricClientCommandSource src) {
		cleanupReplay();
		active = false;
		src.sendFeedback(Component.literal("Stopped spectating."));
	}

	private static void exitReplay() {
		if (active && mode == Mode.REPLAY) finishReplay();
	}

	// ------------------------------------------------------------------ events

	/** Called from the packet mixin on the main thread. */
	public static void onEntityEvent(Entity entity, int id) {
		if (!active || mode != Mode.LIVE || id != TOTEM_EVENT || !(entity instanceof Player p)) return;
		String n = p.getName().getString();
		if (n.equalsIgnoreCase(nameA)) pendingTotemIsA = true;
		else if (n.equalsIgnoreCase(nameB)) pendingTotemIsA = false;
		else return;
		stats.totemPopped(pendingTotemIsA);
	}

	// ------------------------------------------------------------------ tick

	private static Player find(net.minecraft.world.level.Level level, String name) {
		for (Player p : level.players()) if (p.getName().getString().equalsIgnoreCase(name)) return p;
		return null;
	}

	private static void tick(Minecraft mc) {
		if (!active) return;
		LocalPlayer me = mc.player;
		if (me == null || mc.level == null) { active = false; cleanupReplay(); return; }
		if (!me.isSpectator()) return; // never drag a non-spectator around

		if (mode == Mode.REPLAY) { tickReplay(mc, me); return; }

		Player pa = find(mc.level, nameA), pb = find(mc.level, nameB);
		if (pa == null || pb == null) return;

		stats.tick(pa, pb);
		recorder.record(Frame.of(pa), Frame.of(pb));
		if (deathCooldown > 0) deathCooldown--;

		if (deathCooldown == 0 && (pa.getHealth() <= 0 || pb.getHealth() <= 0)) {
			boolean loserIsA = pa.getHealth() <= 0;
			deathCooldown = DEATH_REARM_TICKS;
			startReplay(mc, pa, pb, Cause.DEATH, loserIsA);
			return;
		}
		if (pendingTotemIsA != null) {
			boolean isA = pendingTotemIsA;
			pendingTotemIsA = null;
			startReplay(mc, pa, pb, Cause.TOTEM, isA);
			return;
		}

		// Live camera, following the nearest pearl if one is in flight.
		Vec3 mid = pa.position().add(pb.position()).scale(0.5);
		Entity pearl = null;
		double best = 80 * 80;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e.getType() != EntityType.ENDER_PEARL || e.isRemoved()) continue;
			double d = e.position().distanceToSqr(mid);
			if (d < best) { best = d; pearl = e; }
		}
		var pose = director.update(mc.level, me, pa.position(), pb.position(),
			pearl == null ? null : pearl.position(), pearl == null ? null : pearl.getDeltaMovement());
		applyCamera(me, pose);
	}

	private static void applyCamera(LocalPlayer me, CameraDirector.Pose pose) {
		me.setPos(pose.eye().x, pose.eye().y - me.getEyeHeight(), pose.eye().z);
		me.setDeltaMovement(Vec3.ZERO);
		me.setYRot(pose.yaw());
		me.setXRot(pose.pitch());
		me.setYHeadRot(pose.yaw());
	}

	// ------------------------------------------------------------------ replay

	private static void startReplay(Minecraft mc, Player pa, Player pb, Cause cause, boolean loserIsA) {
		List<Recorder.Tick> frames = recorder.snapshot();
		if (frames.size() < 10) {
			if (cause == Cause.DEATH) showBreakdown(mc, loserIsA);
			return;
		}
		try {
			ghostA = spawnGhost(mc, pa);
			ghostB = spawnGhost(mc, pb);
		} catch (Throwable t) {
			System.err.println("[relay_spectator] could not create replay ghosts: " + t);
			cleanupReplay();
			if (cause == Cause.DEATH) showBreakdown(mc, loserIsA);
			return;
		}
		for (Player real : List.of(pa, pb)) {
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
		mc.gui.getChat().addMessage(Component.literal(
			"Replay: last " + frames.size() / 20 + "s before the " + (cause == Cause.TOTEM ? "totem pop" : "death") + ". /replay exit to skip."));
	}

	private static RemotePlayer spawnGhost(Minecraft mc, Player src) {
		String name = src.getName().getString();
		// Fresh UUID: the level refuses a second entity with the real player's UUID.
		// Side effect: ghosts use the default skin and carry a name tag instead.
		RemotePlayer g = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), name));
		g.setId(-9000 - (int) (Math.random() * 1000));
		g.setPos(src.position());
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
		applyCamera(me, director.update(mc.level, me, t.a().pos(), t.b().pos(), null, null));
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
		lastBreakdown = new BreakdownScreen(nameA, nameB, kit, stats, aWon, scoreA, scoreB);
		stats = new Stats(); // fresh numbers for the next round
		mc.setScreen(lastBreakdown);
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
