package io.github.arturopakastur.crossview.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import io.github.arturopakastur.crossview.CrossView;
import net.fabricmc.loader.api.FabricLoader;

/**
 * All CrossView settings.
 *
 * Defaults: everything that changes vanilla is OFF (3D Mode = Off, no desaturation, no legacy
 * shift, no inversion). The "3D" switch is left ON so that picking a mode works immediately, and
 * the crosshair stays on because that is how vanilla looks.
 *
 * Settings are saved to config/crossview.json (loaded automatically the first time this class is
 * touched, which happens very early on startup, before the window sizes are decided).
 */
public final class CrossViewConfig {
	private CrossViewConfig() {
	}

	public enum Mode {
		OFF("Off"),
		SIDE_BY_SIDE("Side By Side"),
		ANAGLYPH("Anaglyph");

		public final String label;

		Mode(String label) {
			this.label = label;
		}
	}

	public enum AnaglyphType {
		RED_CYAN("Red-Cyan"),
		GREEN_MAGENTA("Green-Magenta"),
		BLUE_YELLOW("Blue-Yellow");

		public final String label;

		AnaglyphType(String label) {
			this.label = label;
		}
	}

	// ---- Settings (original option name in the comment) ----
	public static Mode mode = Mode.OFF;                    // "3D Mode"
	public static boolean enabled = true;                  // "3D"
	public static boolean invert = false;                  // "Invert 3D"
	public static boolean vignette = false;                // "Vignette"
	public static int power = 5;                           // "3D Power", 1..10
	public static boolean crosshair = true;                // "Crosshair"
	public static int crosshairDepth = 0;                  // "Crosshair Depth" slider, -50..50 in steps of 1 (an offset when Dynamic Crosshair is on)
	public static AnaglyphType anaglyphType = AnaglyphType.RED_CYAN; // "Anaglyph Type"
	public static boolean legacy3d = true;                 // "Legacy 3D"
	public static int desaturate = 0;                      // "Desaturate", 0..100

	// ---- Dynamic Crosshair Depth (new, not in the original mod) ----
	/**
	 * Side By Side only. The crosshair depth follows whatever is under the crosshair (block, entity
	 * or sky), and "Crosshair Depth" becomes an offset (in pixels, per eye) from that automatic
	 * depth. 0 = exactly on the target. Does nothing in Anaglyph or with 3D off.
	 */
	public static boolean dynamicCrosshair = true;        // "Dynamic Crosshair"
	/**
	 * How fast the automatic depth follows the target, in 1/seconds. 0 or less = instant, no
	 * smoothing. The settings screen cycles through the presets below ("Dynamic Speed"); any other
	 * number written in the JSON file still works and is shown as "Custom".
	 */
	public static double dynamicSpeed = 10.0;                // "Dynamic Speed"
	private static final String[] SPEED_LABELS = {"Slow", "Normal", "Fast", "Instant"};
	private static final double[] SPEED_VALUES = {5.0, 10.0, 100.0, 0.0};
	/**
	 * Fine-tuning multiplier on the calibrated distance scale. 1.0 = exactly matches the reference
	 * measurement in CrossViewDepthMath. Raise it if the crosshair looks too close to you for
	 * everything, lower it if too far. JSON only.
	 */
	public static double dynamicCalibration = 1.0;

	// ---- Helpers. The cycle methods wrap around at the end, like the original buttons. ----

	// In the original, the screen LAYOUT depends on the mode alone. Turning "3D" off
	// (the H key) only removes the parallax; side-by-side still draws two halves.

	/**
	 * Becomes true once the game has finished loading (set by CrossViewClient). Not saved.
	 * Side By Side stays off until then: while the game is still loading, our custom shaders do
	 * not exist yet, so drawing the two-eye image would fail. After loading, switching it on
	 * behaves exactly like switching it on from the settings screen in a world.
	 */
	public static boolean gameReady = false;

	/** Screen is split into left/right halves, everything drawn twice. */
	public static boolean isSideBySide() {
		return mode == Mode.SIDE_BY_SIDE && gameReady;
	}

	/** Two full-size passes with per-eye color masks. */
	public static boolean isAnaglyph() {
		return mode == Mode.ANAGLYPH;
	}

	/** Eye offsets, legacy projection shift and crosshair shift only apply when this is true. */
	public static boolean parallaxActive() {
		return enabled && mode != Mode.OFF;
	}

	/** Dynamic Crosshair Depth is active: Side By Side, 3D on, option on. */
	public static boolean dynamicCrosshairActive() {
		return dynamicCrosshair && isSideBySide() && parallaxActive();
	}

	/** Index into the Dynamic Speed presets, or -1 if the saved value is not one of them. */
	private static int dynamicSpeedIndex() {
		for (int i = 0; i < SPEED_VALUES.length; i++) {
			if (Math.abs(dynamicSpeed - SPEED_VALUES[i]) < 1.0E-9) {
				return i;
			}
		}
		return -1;
	}

	/** Label for the settings screen: Slow / Normal / Fast / Instant (or Custom for a hand-edited value). */
	public static String dynamicSpeedLabel() {
		int index = dynamicSpeedIndex();
		return index >= 0 ? SPEED_LABELS[index] : "Custom";
	}

	/** Slow -> Normal -> Fast -> Instant -> Slow. A custom value moves to Slow. */
	public static void cycleDynamicSpeed() {
		dynamicSpeed = SPEED_VALUES[(dynamicSpeedIndex() + 1) % SPEED_VALUES.length];
	}

	public static void cycleMode() {
		Mode[] all = Mode.values();
		mode = all[(mode.ordinal() + 1) % all.length];
	}

	public static void cycleAnaglyphType() {
		AnaglyphType[] all = AnaglyphType.values();
		anaglyphType = all[(anaglyphType.ordinal() + 1) % all.length];
	}

	public static void cyclePower() {
		power = power >= 10 ? 1 : power + 1;
	}

	public static void setCrosshairDepth(int value) {
		crosshairDepth = Math.clamp(value, -50, 50);
	}

	public static void setDesaturate(int value) {
		desaturate = Math.clamp(value, 0, 100);
	}

	// ---- Saving and loading ----

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("crossview.json");

	// Must stay below the field initializers above: it runs the first time this class is used.
	static {
		load();
	}

	/** Reads config/crossview.json. Missing or broken values fall back to the defaults. */
	public static void load() {
		if (!Files.isRegularFile(FILE)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				throw new IllegalStateException("top level is not a JSON object");
			}

			JsonObject json = root.getAsJsonObject();
			mode = readEnum(json, "mode", Mode.class, mode);
			enabled = readBoolean(json, "enabled", enabled);
			invert = readBoolean(json, "invert", invert);
			vignette = readBoolean(json, "vignette", vignette);
			power = readInt(json, "power", power, 1, 10, 1);
			crosshair = readBoolean(json, "crosshair", crosshair);
			crosshairDepth = readInt(json, "crosshairDepth", crosshairDepth, -50, 50, 1);
			anaglyphType = readEnum(json, "anaglyphType", AnaglyphType.class, anaglyphType);
			legacy3d = readBoolean(json, "legacy3d", legacy3d);
			desaturate = readInt(json, "desaturate", desaturate, 0, 100, 1);
			dynamicCrosshair = readBoolean(json, "dynamicCrosshair", dynamicCrosshair);
			dynamicSpeed = readDouble(json, "dynamicSpeed", dynamicSpeed, 0.0, 1000.0);
			dynamicCalibration = readDouble(json, "dynamicCalibration", dynamicCalibration, 0.5, 2.0);
		} catch (Exception e) {
			CrossView.LOGGER.warn("Could not read {}; using default settings and keeping a copy as .bad", FILE, e);
			try {
				Files.move(FILE, FILE.resolveSibling("crossview.json.bad"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// Not important; the next save overwrites the file anyway.
			}
		}
	}

	/** Writes the current settings to config/crossview.json. Safe to call often. */
	public static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("mode", mode.name());
		json.addProperty("enabled", enabled);
		json.addProperty("invert", invert);
		json.addProperty("vignette", vignette);
		json.addProperty("power", power);
		json.addProperty("crosshair", crosshair);
		json.addProperty("crosshairDepth", crosshairDepth);
		json.addProperty("anaglyphType", anaglyphType.name());
		json.addProperty("legacy3d", legacy3d);
		json.addProperty("desaturate", desaturate);
		json.addProperty("dynamicCrosshair", dynamicCrosshair);
		json.addProperty("dynamicSpeed", dynamicSpeed);
		json.addProperty("dynamicCalibration", dynamicCalibration);

		try {
			Files.createDirectories(FILE.getParent());
			// Write to a temporary file first so a crash mid-write can never leave a half-written config.
			Path temp = FILE.resolveSibling("crossview.json.tmp");
			Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
			try {
				Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			CrossView.LOGGER.warn("Could not save {}", FILE, e);
		}
	}

	private static boolean readBoolean(JsonObject json, String key, boolean fallback) {
		try {
			JsonElement value = json.get(key);
			return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
					? value.getAsBoolean()
					: fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	/** Reads a decimal number and keeps it inside [min, max]. Bad or non-finite values use the fallback. */
	private static double readDouble(JsonObject json, String key, double fallback, double min, double max) {
		try {
			JsonElement value = json.get(key);
			if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
				return fallback;
			}

			double number = value.getAsDouble();
			return Double.isFinite(number) ? Math.clamp(number, min, max) : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	/** Reads a whole number, keeps it inside [min, max] and rounds it to a multiple of {@code step}. */
	private static int readInt(JsonObject json, String key, int fallback, int min, int max, int step) {
		try {
			JsonElement value = json.get(key);
			if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
				return fallback;
			}

			int number = Math.clamp(Math.round(value.getAsDouble()), min, max);
			if (step > 1) {
				number = Math.clamp(Math.round(number / (double) step) * step, min, max);
			}
			return number;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static <E extends Enum<E>> E readEnum(JsonObject json, String key, Class<E> type, E fallback) {
		try {
			JsonElement value = json.get(key);
			if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
				return fallback;
			}
			return Enum.valueOf(type, value.getAsString().trim().toUpperCase(java.util.Locale.ROOT));
		} catch (RuntimeException e) {
			return fallback;
		}
	}
}
