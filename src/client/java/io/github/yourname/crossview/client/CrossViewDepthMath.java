package io.github.arturopakastur.crossview.client;

/**
 * The geometry behind Dynamic Crosshair Depth. Pure math, no Minecraft classes, so every number
 * here can be checked on its own.
 *
 * <h2>Where the formula comes from</h2>
 * Everything is measured in pixels of ONE eye image (the main render target in Side By Side).
 * A point straight ahead of the camera at distance {@code d} blocks lands on screen like this:
 * <ul>
 *   <li><b>Eye offset (parallax).</b> Each eye is moved {@code EYE_STEP * power} blocks sideways
 *       (see CrossViewEyes), so the point appears {@code f * EYE_STEP * power / d} pixels off
 *       centre, in opposite directions for the two eyes. {@code f} is the focal length in pixels:
 *       {@code f = eyeHeight / (2 * tan(fov / 2))}. This is the only term that depends on
 *       distance, FOV and image height.</li>
 *   <li><b>Legacy 3D shift.</b> Legacy 3D slides the whole projected image sideways by a fixed
 *       {@code LEGACY_SHIFT * power} in NDC units (-1..1 across the image), the same at every
 *       distance. In pixels that is {@code LEGACY_SHIFT * power * eyeWidth / 2}, in the direction
 *       that makes the eyes cross. It does not depend on FOV.</li>
 *   <li><b>Crosshair Depth</b> moves the crosshair sprite by exactly that many pixels per eye
 *       (CrossViewSideBySide.crosshairShift).</li>
 * </ul>
 * The crosshair sits exactly on the target when its shift equals the target's shift:
 * <pre>
 *   depth(d) = kappa * EYE_STEP * power * f / d  -  [Legacy 3D] LEGACY_SHIFT * power * eyeWidth / 2
 *   d(depth) = kappa * EYE_STEP * power * f / (depth + [Legacy 3D] LEGACY_SHIFT * power * eyeWidth / 2)
 * </pre>
 * Both are linear in 1/d, which is why the mod smooths 1/d and not d.
 *
 * <h2>The one calibrated number: kappa</h2>
 * {@code kappa} is a plain distance-scale factor on the parallax term. 1.0 would be the pure
 * geometry above. It is computed from ONE hand-measured reference point (below) so that the model
 * reproduces it exactly. All other combinations (Legacy 3D on/off, any Power, any Crosshair
 * Depth, any FOV, any resolution) then follow from the geometry. Against the 8 hand
 * measurements this was derived from, the other 7 come out within 2%.
 */
public final class CrossViewDepthMath {
	private CrossViewDepthMath() {
	}

	/** Sideways eye offset per 3D Power point, per eye, in blocks. Same as CrossViewEyes (0.02 * power). */
	public static final double EYE_STEP = 0.02;

	/** Legacy 3D image shift per 3D Power point, per eye, in NDC units. Same as CrossViewEyes (0.07 * power / 5). */
	public static final double LEGACY_SHIFT = 0.07 / 5.0;

	// ---- Reference measurement (hand measured, FOV 90, game window 1920x1080 -> each eye 960x1080) ----
	// Legacy 3D ON, 3D Power 5, Crosshair Depth 10: the crosshair is perfect 1.3 blocks from the block.
	public static final double REF_DISTANCE = 1.3;
	public static final double REF_POWER = 5.0;
	public static final double REF_DEPTH = 10.0;
	public static final double REF_FOV_DEGREES = 90.0;
	public static final double REF_EYE_WIDTH = 960.0;
	public static final double REF_EYE_HEIGHT = 1080.0;

	/** kappa that makes the model reproduce the reference measurement exactly. About 1.0496. */
	public static final double ANCHOR_CALIBRATION = anchorCalibration();

	private static double anchorCalibration() {
		double focal = focalPixelsFromFov(REF_FOV_DEGREES, REF_EYE_HEIGHT);
		double parallaxAtOneBlock = EYE_STEP * REF_POWER * focal;
		double legacy = legacyShiftPixels(REF_POWER, REF_EYE_WIDTH);
		// depth = kappa * parallaxAtOneBlock / d - legacy   =>   kappa = (depth + legacy) * d / parallaxAtOneBlock
		return (REF_DEPTH + legacy) * REF_DISTANCE / parallaxAtOneBlock;
	}

	/** Focal length in pixels from the VERTICAL field of view (Minecraft's FOV option is vertical). */
	public static double focalPixelsFromFov(double fovDegrees, double eyeHeightPx) {
		return eyeHeightPx / (2.0 * Math.tan(Math.toRadians(fovDegrees) / 2.0));
	}

	/**
	 * Focal length in pixels from a projection matrix. m11 of a perspective matrix is 1 / tan(fov / 2),
	 * so this follows the FOV that is really being rendered (sprinting, speed effects, underwater, ...).
	 */
	public static double focalPixelsFromProjection(double projectionM11, double eyeHeightPx) {
		return projectionM11 * eyeHeightPx / 2.0;
	}

	/** Legacy 3D shift of one eye, in pixels. */
	public static double legacyShiftPixels(double power, double eyeWidthPx) {
		return LEGACY_SHIFT * power * eyeWidthPx / 2.0;
	}

	/**
	 * Crosshair Depth (pixels per eye) that puts the crosshair exactly on a target at 1 / distance.
	 * {@code invDistance} 0 means infinitely far (sky). The result is NOT limited to the slider range.
	 */
	public static double depthForInverseDistance(double invDistance, double power, boolean legacy3d,
			double focalPx, double eyeWidthPx, double calibration) {
		double depth = calibration * EYE_STEP * power * focalPx * invDistance;
		if (legacy3d) {
			depth -= legacyShiftPixels(power, eyeWidthPx);
		}
		return depth;
	}

	/** Same as above, for a distance in blocks. */
	public static double depthForDistance(double distance, double power, boolean legacy3d,
			double focalPx, double eyeWidthPx, double calibration) {
		return depthForInverseDistance(1.0 / distance, power, legacy3d, focalPx, eyeWidthPx, calibration);
	}

	/**
	 * The inverse: how far away (blocks) a crosshair with the given Crosshair Depth looks right.
	 * Returns +Infinity when the two lines of sight never meet (parallel or diverging).
	 */
	public static double distanceForDepth(double depth, double power, boolean legacy3d,
			double focalPx, double eyeWidthPx, double calibration) {
		double denominator = depth + (legacy3d ? legacyShiftPixels(power, eyeWidthPx) : 0.0);
		if (denominator <= 0.0) {
			return Double.POSITIVE_INFINITY;
		}
		return calibration * EYE_STEP * power * focalPx / denominator;
	}
}
