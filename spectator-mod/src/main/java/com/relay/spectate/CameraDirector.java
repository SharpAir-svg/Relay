package com.relay.spectate;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Picks and smooths cinematic shots of a duel, with two guarantees:
 * <ul>
 * <li>the camera (a box around it, not just a point) is never inside or against a block, and</li>
 * <li>the shot it settles on can see both fighters; if the current view gets blocked it cuts to the
 * best clear spot it can find, searching a ring of positions around the fight.</li>
 * </ul>
 */
public final class CameraDirector {
	public record Pose(Vec3 eye, float yaw, float pitch) {}

	private static final int SHOTS = 7;
	private static final double MARGIN = 0.45;      // clearance kept from blocks
	private static final double MIN_DIST = 1.5;     // never closer than this to the action
	private static final double[] BODY = { 0.5, 1.5 }; // heights on a fighter that must be visible (torso, head)
	private static final int FULL_SCORE = 4;        // 2 fighters x 2 points
	private static final Vec3[] OFFSETS = offsets();

	private Vec3 camPos, lookAt;
	private float yaw;
	private int shot, shotTicks, blockedTicks;
	private boolean following;
	int dbgShot, dbgWant, dbgActual; // read by the dev camera test
	private Vec3 heldOffset; // a blocked-view fix, held relative to the fighters for a moment

	/** The camera box corners, face centres and centre: rays from all of them must clear for the camera to be safe. */
	private static Vec3[] offsets() {
		List<Vec3> o = new ArrayList<>();
		o.add(Vec3.ZERO);
		for (int x = -1; x <= 1; x += 2) {
			o.add(new Vec3(x * MARGIN, 0, 0));
			o.add(new Vec3(0, x * MARGIN, 0));
			o.add(new Vec3(0, 0, x * MARGIN));
			for (int y = -1; y <= 1; y += 2) for (int z = -1; z <= 1; z += 2) o.add(new Vec3(x * MARGIN, y * MARGIN, z * MARGIN));
		}
		return o.toArray(new Vec3[0]);
	}

	public void reset() {
		camPos = null;
		lookAt = null;
		shotTicks = 0;
		blockedTicks = 0;
		heldOffset = null;
		following = false;
	}

	/**
	 * Called every rendered frame.
	 * @param pearl pearl position or null; @param pearlVel its velocity (may be null)
	 * @param dt seconds since the previous frame (smoothing is frame-rate independent)
	 * @param tickStep true on the first frame after a game tick; the shot timer and the
	 *        (costlier) visibility search only advance then
	 */
	public Pose update(Level level, Entity clipper, Vec3 a, Vec3 b, Vec3 pearl, Vec3 pearlVel, double dt, boolean tickStep) {
		Vec3 mid = a.add(b).scale(0.5).add(0, 1.0, 0);
		Vec3 flat = new Vec3(b.x - a.x, 0, b.z - a.z);
		Vec3 dir = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
		Vec3 perp = new Vec3(-dir.z, 0, dir.x);
		double d = a.distanceTo(b);

		Vec3 wantCam, wantLook;
		double camRate, lookRate; // 1/s: higher = snappier
		if (pearl != null) {
			Vec3 back = (pearlVel != null && pearlVel.lengthSqr() > 1.0E-3) ? pearlVel.normalize() : dir;
			wantLook = pearl;
			wantCam = safe(level, clipper, pearl, pearl.subtract(back.scale(6)).add(0, 2, 0));
			camRate = 7;
			lookRate = 12;
			if (!following) camPos = null; // snap to the pearl shot
		} else {
			if (tickStep && shotTicks-- <= 0) {
				shot = (shot + 1 + (int) (Math.random() * (SHOTS - 1))) % SHOTS;
				shotTicks = 70 + (int) (Math.random() * 50);
				heldOffset = null;
			}
			wantLook = mid;
			wantCam = safe(level, clipper, mid, candidate(shot, mid, a, b, dir, perp, d));

			if (tickStep) {
				// Judge both where we want to be and where the (smoothed) camera actually is right now.
				int want = score(level, clipper, wantCam, mid, a, b);
				int actual = camPos == null ? want : score(level, clipper, camPos, mid, a, b);
				dbgShot = shot; dbgWant = want; dbgActual = actual;
				blockedTicks = (want < FULL_SCORE || actual < FULL_SCORE) ? blockedTicks + 1 : 0;
				if (blockedTicks >= 3) {
					Vec3 best = findClearSpot(level, clipper, mid, a, b, dir, perp, d, wantCam, want);
					if (best != null) wantCam = best;
					shotTicks = 50; // hold the new spot for a moment instead of cutting again straight away
					blockedTicks = 0;
					heldOffset = wantCam.subtract(mid);
				}
			}
			if (heldOffset != null && shotTicks > 0) wantCam = safe(level, clipper, mid, mid.add(heldOffset));
			camRate = 2.5;
			lookRate = 5;
		}
		following = pearl != null;

		double ca = 1 - Math.exp(-camRate * dt), la = 1 - Math.exp(-lookRate * dt);
		camPos = camPos == null ? wantCam : camPos.lerp(wantCam, ca);
		lookAt = lookAt == null ? wantLook : lookAt.lerp(wantLook, la);
		// The smoothed path can cut corners through walls, so re-validate it every frame.
		camPos = safe(level, clipper, lookAt, camPos);

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
			case 5 -> mid.add(perp.scale(0.5)).add(0, 7 + d * 0.4, 0); // near top-down
			default -> mid.add(dir.scale(-(r * 0.7))).add(perp.scale(r * 0.5)).add(0, 3, 0);
		};
	}

	/**
	 * Searches the named shots plus a ring of positions (12 angles, two radii, three heights, and straight
	 * above) for the spot with the best view of both fighters. Ties go to the spot nearest the current camera
	 * so cuts stay as small as possible.
	 */
	private Vec3 findClearSpot(Level level, Entity clipper, Vec3 mid, Vec3 a, Vec3 b, Vec3 dir, Vec3 perp, double d,
			Vec3 fallback, int fallbackScore) {
		List<Vec3> cands = new ArrayList<>();
		for (int s = 0; s < SHOTS; s++) cands.add(candidate(s, mid, a, b, dir, perp, d));
		double r0 = Math.max(4.0, d * 1.1 + 3.0);
		for (double r : new double[] { r0, r0 * 0.6 }) {
			for (double h : new double[] { 1.5, 3.5, 6.5 }) {
				for (int i = 0; i < 12; i++) {
					double ang = Math.toRadians(i * 30);
					cands.add(mid.add(Math.cos(ang) * r, h, Math.sin(ang) * r));
				}
			}
		}
		for (double h : new double[] { 4, 8, 14 }) cands.add(mid.add(0, h, 0)); // straight above: the last resort

		boolean dbg = System.getProperty("relay.demo.cam") != null;
		StringBuilder log = dbg ? new StringBuilder() : null;
		Vec3 best = fallback;
		int bestScore = fallbackScore;
		double bestDist = camPos == null ? 0 : fallback.distanceToSqr(camPos);
		for (Vec3 c : cands) {
			Vec3 s = safe(level, clipper, mid, c);
			int sc = score(level, clipper, s, mid, a, b);
			if (dbg) log.append(sc);
			if (sc < bestScore) continue;
			double dist = camPos == null ? 0 : s.distanceToSqr(camPos);
			if (sc > bestScore || dist < bestDist) { best = s; bestScore = sc; bestDist = dist; }
		}
		if (dbg) System.out.println("[relay_spectator] search fallback=" + fallbackScore + " cands=" + log + " best=" + bestScore);
		return best;
	}

	/**
	 * 0 to 4: how many of the fighters' torso and head points the camera sees through no block.
	 * A camera hugging the action (closer than {@link #MIN_DIST}) loses a point so it doesn't win by default.
	 */
	private static int score(Level level, Entity clipper, Vec3 cam, Vec3 mid, Vec3 a, Vec3 b) {
		int n = 0;
		for (double dy : BODY) {
			if (clear(level, clipper, cam, a.add(0, dy, 0))) n++;
			if (clear(level, clipper, cam, b.add(0, dy, 0))) n++;
		}
		if (cam.distanceTo(mid) < MIN_DIST) n--;
		return n;
	}

	private static boolean clear(Level level, Entity clipper, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, clipper))
			.getType() == HitResult.Type.MISS;
	}

	/** A camera position that is not in, or touching, any block: pulled in from {@code cam} toward {@code target}. */
	private static Vec3 safe(Level level, Entity clipper, Vec3 target, Vec3 cam) {
		Vec3 p = pullIn(level, clipper, target, cam);
		// Belt and braces: if a block still overlaps the camera box, walk toward the target until it doesn't.
		Vec3 toTarget = target.subtract(p);
		double len = toTarget.length();
		if (len < 1.0E-4) return p;
		Vec3 step = toTarget.scale(0.25 / len);
		for (int i = 0; i < 40 && boxBlocked(level, clipper, p) && p.distanceTo(target) > 0.3; i++) p = p.add(step);
		return p;
	}

	private static boolean boxBlocked(Level level, Entity clipper, Vec3 p) {
		AABB box = new AABB(p, p).inflate(MARGIN * 0.8);
		return level.getBlockCollisions(clipper, box).iterator().hasNext();
	}

	/**
	 * Shortens target->cam so the camera box stays clear of blocks. Rays are cast from the box's corners
	 * and face centres so the camera can't clip a corner or graze a wall.
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
		// Clearance from blocks always wins; being close to the action only costs the shot points (see score()).
		double dist = Math.max(0, t * len - MARGIN);
		return target.add(dir.scale(dist / len));
	}
}
