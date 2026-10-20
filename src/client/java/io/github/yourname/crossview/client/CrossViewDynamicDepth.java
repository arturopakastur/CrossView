package io.github.arturopakastur.crossview.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

/**
 * Dynamic Crosshair Depth (Side By Side only).
 *
 * angle: the world is NOT touched, only the per-eye crosshair shift changes. The math is in
 * {@link CrossViewDepthMath}; this class finds the distance and keeps the per-frame state.
 *
 * Once per frame (before the two eyes are built, so both eyes agree):
 *  1. A ray is cast from the camera along its view direction. The nearest of block (outline
 *     shape, water surface included), entity, or nothing (sky) gives the distance.
 *  2. 1 / distance is smoothed. Smoothing 1/d (not d) matches how the crosshair really moves on
 *     screen, so near-to-sky changes glide instead of snapping.
 *  3. The projection's focal scale is remembered, so the depth follows the FOV that is really
 *     being rendered (sprint, speed effects, underwater, FOV slider).
 *
 * While each eye is built, {@link #currentAutoDepth} turns that into pixels for the current
 * Power, Legacy 3D and eye image size. CrossViewSideBySide adds the Crosshair Depth setting on top.
 */
public final class CrossViewDynamicDepth {
	private CrossViewDynamicDepth() {
	}

	/** Nothing closer than this is used. Closer than this the crosshair would leave the screen anyway. */
	private static final double MIN_DISTANCE = 0.15;
	/** Threshold to detect "the ray starts inside a block or fluid". */
	private static final double INSIDE_THRESHOLD = 0.15;
	/** Entities farther than this are ignored (their effect is below one pixel anyway). */
	private static final double ENTITY_RANGE = 256.0;

	/** Smoothed 1 / distance in 1/blocks. 0 = infinitely far. */
	private static double invDistance = 0.0;
	/** m11 of the level projection = 1 / tan(fov / 2) of the frame being rendered. */
	private static double projectionM11 = 1.0;
	private static long lastNanos = 0L;

	/**
	 * Called once per frame from CrossViewSideBySide, before the eye loop.
	 *
	 * @param baseProjection the level projection before any eye shift
	 */
	public static void update(Minecraft minecraft, GameRenderer gameRenderer, CameraRenderState camera, Matrix4f baseProjection) {
		if (!CrossViewConfig.dynamicCrosshairActive() || !camera.initialized
				|| minecraft.level == null || minecraft.player == null) {
			lastNanos = 0L;
			return;
		}

		projectionM11 = baseProjection.m11();

		double distance = findDistance(minecraft, gameRenderer, camera);
		double target = Double.isInfinite(distance) ? 0.0 : 1.0 / distance;

		long now = System.nanoTime();
		double speed = CrossViewConfig.dynamicSpeed;
		if (speed <= 0.0 || lastNanos == 0L) {
			invDistance = target;
		} else {
			double dt = Math.min((now - lastNanos) / 1.0E9, 0.25);
			invDistance += (target - invDistance) * (1.0 - Math.exp(-speed * dt));
			if (Math.abs(target - invDistance) < 1.0E-7) {
				invDistance = target;
			}
		}
		lastNanos = now;
	}

	/** Forget the smoothing state (used when the world is not drawn). The next frame jumps straight to its target. */
	public static void reset() {
		lastNanos = 0L;
	}

	/**
	 * The automatic Crosshair Depth in pixels per eye for the current frame. Not limited to the slider
	 * range: with Legacy 3D on, far targets need strongly negative values.
	 *
	 * @param eyeWidth  width of one eye image in pixels (the main render target)
	 * @param eyeHeight height of one eye image in pixels
	 */
	public static double currentAutoDepth(int eyeWidth, int eyeHeight) {
		double focal = CrossViewDepthMath.focalPixelsFromProjection(projectionM11, eyeHeight);
		double calibration = CrossViewDepthMath.ANCHOR_CALIBRATION * CrossViewConfig.dynamicCalibration;
		return CrossViewDepthMath.depthForInverseDistance(
				invDistance, CrossViewConfig.power, CrossViewConfig.legacy3d, focal, eyeWidth, calibration);
	}

	// ------------------------------------------------------------------ distance

	/**
	 * Distance in blocks from the camera to whatever is under the crosshair, or +Infinity for nothing.
	 * Block/fluid/entity handling with a ray as long as the render distance.
	 */
	private static double findDistance(Minecraft minecraft, GameRenderer gameRenderer, CameraRenderState camera) {
		Entity camEntity = minecraft.getCameraEntity() != null ? minecraft.getCameraEntity() : minecraft.player;
		var level = minecraft.level;

		// Exactly the ray through the middle of the screen (the crosshair is drawn there, first person only).
		Vector3fc forward = gameRenderer.mainCamera().forwardVector();
		Vec3 dir = new Vec3(forward.x(), forward.y(), forward.z());
		Vec3 start = camera.pos;

		double range = Math.clamp(minecraft.options.getEffectiveRenderDistance() * 16.0 + 16.0, 32.0, 1024.0);
		Vec3 end = start.add(dir.scale(range));

		BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, camEntity));
		HitResult result = blockHit;
		double dist = result.getLocation().distanceTo(start);

		// Camera inside a fluid or a block without a shape (cobweb, tall grass, ...): look through it.
		if (dist < INSIDE_THRESHOLD && result.getType() == HitResult.Type.BLOCK) {
			var pos = blockHit.getBlockPos();
			if (!level.getFluidState(pos).isEmpty()) {
				result = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camEntity));
				dist = result.getLocation().distanceTo(start);
			} else if (level.getBlockState(pos).getShape(level, pos).isEmpty()) {
				result = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, camEntity));
				dist = result.getLocation().distanceTo(start);
			}
		}

		// An entity in front of the block (or in front of the sky) wins.
		double entityRange = Math.min(dist, ENTITY_RANGE);
		if (entityRange > 0.5) {
			Vec3 reach = dir.scale(entityRange);
			EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
					camEntity,
					start,
					start.add(reach),
					camEntity.getBoundingBox().expandTowards(reach).inflate(1.0),
					e -> !e.isSpectator() && e.isPickable(),
					entityRange * entityRange
			);
			if (entityHit != null && entityHit.getType() != HitResult.Type.MISS) {
				return Math.max(entityHit.getLocation().distanceTo(start), MIN_DISTANCE);
			}
		}

		if (result.getType() == HitResult.Type.MISS) {
			return Double.POSITIVE_INFINITY;
		}
		return Math.max(dist, MIN_DISTANCE);
	}
}
