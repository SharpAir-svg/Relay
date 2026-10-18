package com.relay.spectate;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Picks and smooths cinematic shots of a duel. Pure maths; no game state. */
public final class CameraDirector {
	public record Pose(Vec3 eye, float yaw, float pitch) {}

	private static final int SHOTS = 5;

	private Vec3 camPos, lookAt;
	private float yaw;
	private int shot, shotTicks;
	private boolean following;

	public void reset() {
		camPos = null;
		lookAt = null;
		shotTicks = 0;
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
			wantCam = pearl.subtract(back.scale(6)).add(0, 2, 0);
			wantLook = pearl;
			camLerp = 0.3;
			lookLerp = 0.45;
			if (!following) camPos = null; // snap to the pearl shot
		} else {
			if (shotTicks-- <= 0) {
				shot = (shot + 1 + (int) (Math.random() * (SHOTS - 1))) % SHOTS;
				shotTicks = 70 + (int) (Math.random() * 50);
			}
			double r = Math.max(4.0, d * 1.1 + 3.0);
			wantCam = switch (shot) {
				case 0 -> mid.add(perp.scale(r)).add(0, 1.5, 0);
				case 1 -> a.subtract(dir.scale(3.5)).add(perp.scale(0.8)).add(0, 2.2, 0);
				case 2 -> b.add(dir.scale(3.5)).add(perp.scale(-0.8)).add(0, 2.2, 0);
				case 3 -> mid.add(perp.scale(d * 0.6 + 3)).add(0, 5 + d * 0.3, 0);
				default -> mid.add(perp.scale(-r)).add(0, 1.5, 0);
			};
			wantLook = mid;
			camLerp = 0.1;
			lookLerp = 0.2;
		}
		following = pearl != null;

		// Pull the camera in front of any wall between it and the action.
		var hit = level.clip(new ClipContext(wantLook, wantCam, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, clipper));
		if (hit.getType() != HitResult.Type.MISS) {
			Vec3 toCam = wantCam.subtract(wantLook).normalize();
			wantCam = hit.getLocation().subtract(toCam.scale(0.3));
		}

		camPos = camPos == null ? wantCam : camPos.lerp(wantCam, camLerp);
		lookAt = lookAt == null ? wantLook : lookAt.lerp(wantLook, lookLerp);

		Vec3 v = lookAt.subtract(camPos);
		double h = Math.sqrt(v.x * v.x + v.z * v.z);
		float targetYaw = (float) (Math.toDegrees(Math.atan2(-v.x, v.z)));
		float pitch = (float) (-Math.toDegrees(Math.atan2(v.y, h)));
		// Unwrap so the camera never spins the long way round.
		float diff = ((targetYaw - yaw + 540f) % 360f) - 180f;
		yaw += diff;
		return new Pose(camPos, yaw, Math.max(-89f, Math.min(89f, pitch)));
	}
}
