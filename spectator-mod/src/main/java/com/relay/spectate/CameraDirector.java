package com.relay.spectate;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Picks and smooths cinematic shots of a duel. Every camera position is kept
 * out of blocks (with a margin) and each shot is checked for line of sight to
 * both fighters, switching to a clear shot when the current one is blocked.
 */
public final class CameraDirector {
	public record Pose(Vec3 eye, float yaw, float pitch) {}

	private static final int SHOTS = 7;
	private static final double MARGIN = 0.3, MIN_DIST = 0.8, EYE = 1.5;
	private static final Vec3[] OFFSETS = {
		Vec3.ZERO,
		new Vec3(MARGIN, 0, 0), new Vec3(-MARGIN, 0, 0),
		new Vec3(0, MARGIN, 0), new Vec3(0, -MARGIN, 0),
		new Vec3(0, 0, MARGIN), new Vec3(0, 0, -MARGIN)
	};

	private Vec3 camPos, lookAt;
	private float yaw;
	private int shot, shotTicks, blockedTicks;
	private boolean following;

	public void reset() {
		camPos = null;
		lookAt = null;
		shotTicks = 0;
		blockedTicks = 0;
		following = false;
	}

	/** @param pearl pearl position or null; @param pearlVel its velocity (may be null). */
	public Pose update(Level level, Entity clipper, Vec3 a, Vec3 b, Vec3 pearl, Vec3 pearlVel) {
		Vec3 mid = a.add(b).scale(0.5).add(0, 1.0, 0);
		Vec3 flat = new Vec3(b.x - a.x, 0, b.z - a.z);
		Vec3 dir = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
		Vec3 perp = new Vec3(-dir.z, 0, dir.x);
		double d = a.distanceTo(b);

		Vec3 wantCam, wantLook;
		double camLerp, lookLerp;
		if (pearl != null) {
			Vec3 back = (pearlVel != null && pearlVel.lengthSqr() > 1.0E-3) ? pearlVel.normalize() : dir;
			wantLook = pearl;
			wantCam = pullIn(level, clipper, pearl, pearl.subtract(back.scale(6)).add(0, 2, 0));
			camLerp = 0.3;
			lookLerp = 0.45;
			if (!following) camPos = null; // snap to the pearl shot
		} else {
			if (shotTicks-- <= 0) {
				shot = (shot + 1 + (int) (Math.random() * (SHOTS - 1))) % SHOTS;
				shotTicks = 70 + (int) (Math.random() * 50);
			}
			wantLook = mid;
			wantCam = pullIn(level, clipper, mid, candidate(shot, mid, a, b, dir, perp, d));

			// Cut away if the shot can't see both fighters for a few ticks.
			blockedTicks = visibleCount(level, clipper, wantCam, a, b) < 2 ? blockedTicks + 1 : 0;
			if (blockedTicks >= 4) {
				int bestShot = shot, bestScore = visibleCount(level, clipper, wantCam, a, b);
				Vec3 bestCam = wantCam;
				for (int i = 1; i <= SHOTS && bestScore < 2; i++) {
					int s = (shot + i) % SHOTS;
					Vec3 c = pullIn(level, clipper, mid, candidate(s, mid, a, b, dir, perp, d));
					int score = visibleCount(level, clipper, c, a, b);
					if (score > bestScore) { bestScore = score; bestShot = s; bestCam = c; }
				}
				shot = bestShot;
				wantCam = bestCam;
				shotTicks = 70;
				blockedTicks = 0;
			}
			camLerp = 0.1;
			lookLerp = 0.2;
		}
		following = pearl != null;

		camPos = camPos == null ? wantCam : camPos.lerp(wantCam, camLerp);
		lookAt = lookAt == null ? wantLook : lookAt.lerp(wantLook, lookLerp);
		// The smoothed path can cut corners through walls, so re-validate it every tick.
		camPos = pullIn(level, clipper, lookAt, camPos);

		Vec3 v = lookAt.subtract(camPos);
		double h = Math.sqrt(v.x * v.x + v.z * v.z);
		float targetYaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(v.y, h));
		// Unwrap so the camera never spins the long way round.
		float diff = ((targetYaw - yaw + 540f) % 360f) - 180f;
		yaw += diff;
		return new Pose(camPos, yaw, Math.max(-89f, Math.min(89f, pitch)));
	}

	private static Vec3 candidate(int shot, Vec3 mid, Vec3 a, Vec3 b, Vec3 dir, Vec3 perp, double d) {
		double r = Math.max(4.0, d * 1.1 + 3.0);
		return switch (shot) {
			case 0 -> mid.add(perp.scale(r)).add(0, 1.5, 0);
			case 1 -> a.subtract(dir.scale(3.5)).add(perp.scale(0.8)).add(0, 2.2, 0);
			case 2 -> b.add(dir.scale(3.5)).add(perp.scale(-0.8)).add(0, 2.2, 0);
			case 3 -> mid.add(perp.scale(d * 0.6 + 3)).add(0, 5 + d * 0.3, 0);
			case 4 -> mid.add(perp.scale(-r)).add(0, 1.5, 0);
			case 5 -> mid.add(perp.scale(0.5)).add(0, 7 + d * 0.4, 0); // near top-down: usually clear indoors too
			default -> mid.add(dir.scale(-(r * 0.7))).add(perp.scale(r * 0.5)).add(0, 3, 0);
		};
	}

	/** How many of the two fighters (0-2) the camera can see without a block in between. */
	private static int visibleCount(Level level, Entity clipper, Vec3 cam, Vec3 a, Vec3 b) {
		int n = 0;
		if (clear(level, clipper, cam, a.add(0, EYE, 0))) n++;
		if (clear(level, clipper, cam, b.add(0, EYE, 0))) n++;
		return n;
	}

	private static boolean clear(Level level, Entity clipper, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, clipper))
			.getType() == HitResult.Type.MISS;
	}

	/**
	 * Shortens target->cam so the camera, and a small box around it, stays out of blocks.
	 * Several parallel rays are cast so the camera can't clip a corner or graze a wall.
	 */
	private static Vec3 pullIn(Level level, Entity clipper, Vec3 target, Vec3 cam) {
		Vec3 dir = cam.subtract(target);
		double len = dir.length();
		if (len < 1.0E-4) return cam;
		double t = 1.0;
		for (Vec3 off : OFFSETS) {
			Vec3 from = target.add(off), to = cam.add(off);
			var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, clipper));
			if (hit.getType() != HitResult.Type.MISS) t = Math.min(t, hit.getLocation().distanceTo(from) / len);
		}
		if (t >= 1.0) return cam;
		double dist = Math.max(Math.min(MIN_DIST, len), t * len - MARGIN);
		return target.add(dir.scale(dist / len));
	}
}
