package io.github.arturopakastur.crossview.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Optional;
import io.github.arturopakastur.crossview.CrossView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4f;

/**
 * Side By Side mode, following the original mod.
 *
 * The window getters are halved while this mode is on (see WindowMixin), so everything - camera
 * aspect, GUI scale, menus, the main render target - is laid out for ONE eye. Each frame is then
 * rendered twice:
 *   eye 0 -> left half,  eye 1 -> right half.
 *
 * How the two eyes get into one image: eye 0 is rendered and copied aside (a plain same-size
 * copy); eye 1 is rendered into the main target; then ONE shader pass writes a full-width image
 * whose left half shows eye 0 and whose right half shows eye 1. That image is what gets shown on
 * screen instead of the main target (see MinecraftMixin).
 *
 * Why a shader and not two texture copies: vanilla's OpenGL texture copy ignores the destination
 * offset (it passes width/height where a corner coordinate is expected), so it cannot place an
 * image in the right half; and render passes cannot change the viewport, only clip.
 *
 * Because the whole frame is repeated, the world, hand, HUD and menus all appear in both halves.
 * "3D" off removes the parallax but keeps the two halves, like the original.
 *
 * Default viewing: eye 0 is the viewer's RIGHT eye, drawn on the LEFT, so the default is
 * cross-eyed viewing ("CrossView"). "Invert 3D" gives parallel viewing.
 */
public final class CrossViewSideBySide {
	private CrossViewSideBySide() {
	}

	/** 0 or 1 while an eye is being built, otherwise -1. */
	private static int currentEye = -1;

	/** The main render target (one eye wide). Its size is what the crosshair depth is measured in. */
	private static RenderTarget mainTarget;

	private static TextureTarget eyeZeroCopy;
	private static TextureTarget composite;
	private static boolean compositeReady;
	/** If composing fails, wait this long before trying again (it is never given up for the session). */
	private static final long RETRY_DELAY_NANOS = 2_000_000_000L;

	private static int composeFailures;
	private static boolean waitingToRetry;
	private static long retryAtNanos;

	/** Called instead of GameRenderer.render() inside Minecraft.renderFrame(). */
	public static void render(Minecraft minecraft, GameRenderer gameRenderer, Runnable renderOnce) {
		compositeReady = false;
		if (!CrossViewConfig.isSideBySide()) {
			renderOnce.run();
			return;
		}

		RenderTarget main = gameRenderer.mainRenderTarget();
		GameRenderState state = gameRenderer.gameRenderState();
		LevelRenderState level = state.levelRenderState;
		CameraRenderState camera = level.cameraRenderState;
		boolean world = state.shouldRenderLevel;

		// Things a full render consumes and a second eye therefore needs again.
		CrossViewEyes.Snapshot lists = world ? CrossViewEyes.Snapshot.take(level) : null;
		Matrix4f baseView = new Matrix4f(camera.viewRotationMatrix);
		Matrix4f baseProjection = new Matrix4f(camera.projectionMatrix);

		mainTarget = main;

		// Dynamic Crosshair Depth: find what is under the crosshair once per frame, before either eye is built.
		if (world) {
			CrossViewDynamicDepth.update(minecraft, gameRenderer, camera, baseProjection);
		} else {
			CrossViewDynamicDepth.reset();
		}

		try {
			for (int eye = 0; eye < 2; eye++) {
				currentEye = eye;

				if (world) {
					if (eye == 1) {
						lists.restoreInto(level);
					}
					if (CrossViewConfig.parallaxActive()) {
						CrossViewEyes.applyEye(camera, baseView, baseProjection, eye);
					}
				}

				// The crosshair is placed differently per eye, so the GUI is built again for each eye.
				minecraft.gui.extractRenderState(minecraft.getDeltaTracker(), world, minecraft.isGameLoadFinished());

				renderOnce.run();

				if (eye == 0) {
					// The first render of a frame may resize the main target, so size the copy afterwards.
					ensureEyeZeroCopy(main);
					eyeZeroCopy.copyColorFrom(main);
				}
			}
		} finally {
			currentEye = -1;
			if (world) {
				camera.viewRotationMatrix.set(baseView);
				camera.projectionMatrix.set(baseProjection);
			}
		}

		composeEyes(main);
	}

	private static void ensureEyeZeroCopy(RenderTarget main) {
		if (eyeZeroCopy == null) {
			eyeZeroCopy = new TextureTarget("CrossView eye 0", main.width, main.height, GpuFormat.RGBA8_UNORM, null);
		} else if (eyeZeroCopy.width != main.width || eyeZeroCopy.height != main.height) {
			eyeZeroCopy.resize(main.width, main.height);
		}
	}

	private static void ensureComposite(RenderTarget main) {
		int width = main.width * 2;
		if (composite == null) {
			composite = new TextureTarget("CrossView side by side", width, main.height, GpuFormat.RGBA8_UNORM, null);
		} else if (composite.width != width || composite.height != main.height) {
			composite.resize(width, main.height);
		}
	}

	/**
	 * Draws both eyes into the full-width composite: eye 0 on the left, eye 1 (the main target) on the right.
	 *
	 * If this fails (for example because the shader is not ready yet), the plain main target is shown
	 * instead and it is tried again a couple of seconds later. Giving up for the whole session after
	 * one early failure used to leave the right half black until the game was restarted.
	 */
	private static void composeEyes(RenderTarget main) {
		if (waitingToRetry) {
			if (System.nanoTime() - retryAtNanos < 0) {
				return;
			}
			waitingToRetry = false;
		}

		try {
			ensureComposite(main);

			try (RenderPass pass = RenderSystem.getDevice()
					.createCommandEncoder()
					.createRenderPass(() -> "CrossView side by side", composite.getColorTextureView(), Optional.empty())) {
				var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
				pass.setPipeline(RenderSystem.getCompiledPipeline(CrossViewPipelines.SBS_COMPOSE));
				pass.setUniform("Sampler0", eyeZeroCopy.getColorTextureView(), sampler);
				pass.setUniform("Sampler1", main.getColorTextureView(), sampler);
				pass.draw(3, 1, 0, 0);
			}

			compositeReady = true;
			composeFailures = 0;
		} catch (RuntimeException e) {
			composeFailures++;
			waitingToRetry = true;
			retryAtNanos = System.nanoTime() + RETRY_DELAY_NANOS;
			if (composeFailures <= 3) {
				CrossView.LOGGER.error("CrossView side-by-side composing failed (attempt {}); will retry shortly", composeFailures, e);
			}
		}
	}

	/**
	 * The texture to show on screen this frame, or null to use the normal main target.
	 * Called once per frame from the swapchain blit.
	 */
	public static GpuTextureView takePresentView() {
		if (!compositeReady || composite == null) {
			return null;
		}
		compositeReady = false;
		return composite.getColorTextureView();
	}

	/**
	 * Horizontal crosshair shift for the eye currently being built, in GUI units.
	 * Original: the crosshair viewport was moved by Crosshair Depth pixels, in opposite directions
	 * for the two eyes, and flipped by Invert 3D. Only in Side By Side, only while 3D is on.
	 *
	 * Dynamic Crosshair on: the pixel shift is the automatic depth for whatever is under the
	 * crosshair (CrossViewDynamicDepth) PLUS the Crosshair Depth setting, which then works as an
	 * offset: 0 = exactly on the target, positive = farther, negative = closer.
	 */
	public static float crosshairShift() {
		if (currentEye < 0 || !CrossViewConfig.isSideBySide() || !CrossViewConfig.parallaxActive()) {
			return 0.0F;
		}

		double pixels = CrossViewConfig.crosshairDepth;
		if (CrossViewConfig.dynamicCrosshair && mainTarget != null) {
			pixels += CrossViewDynamicDepth.currentAutoDepth(mainTarget.width, mainTarget.height);

			// Not a setting limit: just keeps the crosshair inside the eye image (very close targets
			// would otherwise push it off the edge).
			double limit = Math.max(0.0, mainTarget.width / 2.0 - 8.0);
			pixels = Math.clamp(pixels, -limit, limit);
		}

		if (pixels == 0.0) {
			return 0.0F;
		}

		double signed = pixels * (CrossViewConfig.invert ? -1 : 1) * (currentEye == 1 ? 1 : -1);
		int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		return (float) (signed / guiScale);
	}
}
