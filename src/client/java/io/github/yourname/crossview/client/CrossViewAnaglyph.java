package io.github.arturopakastur.crossview.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.Optional;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4f;

/**
 * Anaglyph mode, following the original mod:
 *  - the WORLD is drawn twice, once per eye, with the view shifted sideways;
 *  - each eye keeps only its own color channels (eye 1 owns the channel matching the anaglyph
 *    type, eye 0 owns the other two);
 *  - the hand and the GUI are flat. The hand is drawn inside both eye passes with identical
 *    pixels (its matrices cancel the view matrix), so it looks the same as in the original.
 *
 * How it works: eye 0 is rendered and copied into a private texture; eye 1 is rendered straight
 * into the main target (keeping its own channels); then the eye 0 copy is drawn on top, writing
 * only the channels eye 0 owns.
 *
 * Desaturation (see CrossViewDesaturate) runs on each eye's image right after it is rendered, so
 * it happens BEFORE the channels are combined. Doing it afterwards would wreck the anaglyph.
 */
public final class CrossViewAnaglyph {
	private CrossViewAnaglyph() {
	}

	private static TextureTarget eyeZeroCopy;

	/** Called instead of GameRenderer.renderLevel(). */
	public static void renderLevel(GameRenderer gameRenderer, Runnable renderLevel) {
		RenderTarget main = gameRenderer.mainRenderTarget();

		// With "3D" off both eyes would be identical, so one normal render gives the same picture.
		// (Side By Side draws the whole frame per eye elsewhere; here it is one normal world pass.)
		if (!CrossViewConfig.isAnaglyph() || !CrossViewConfig.enabled) {
			renderLevel.run();
			CrossViewDesaturate.apply(main);
			return;
		}

		LevelRenderState level = gameRenderer.gameRenderState().levelRenderState;
		CameraRenderState camera = level.cameraRenderState;

		CrossViewEyes.Snapshot lists = CrossViewEyes.Snapshot.take(level);
		Matrix4f originalView = new Matrix4f(camera.viewRotationMatrix);
		Matrix4f originalProjection = new Matrix4f(camera.projectionMatrix);

		ensureEyeTarget(main);

		try {
			// ---- eye 0 ----
			CrossViewEyes.applyEye(camera, originalView, originalProjection, 0);
			renderLevel.run();
			CrossViewDesaturate.apply(main);
			eyeZeroCopy.copyColorFrom(main);

			// ---- eye 1 (drawn straight into the main target) ----
			lists.restoreInto(level);
			CrossViewEyes.applyEye(camera, originalView, originalProjection, 1);
			renderLevel.run();
			CrossViewDesaturate.apply(main);
		} finally {
			camera.viewRotationMatrix.set(originalView);
			camera.projectionMatrix.set(originalProjection);
		}

		compositeEyeZero(main);
	}

	private static void ensureEyeTarget(RenderTarget main) {
		if (eyeZeroCopy == null) {
			eyeZeroCopy = new TextureTarget("CrossView eye 0", main.width, main.height, GpuFormat.RGBA8_UNORM, null);
		} else if (eyeZeroCopy.width != main.width || eyeZeroCopy.height != main.height) {
			eyeZeroCopy.resize(main.width, main.height);
		}
	}

	/** Draws the eye 0 copy over the main target, writing only the channels eye 0 owns. */
	private static void compositeEyeZero(RenderTarget main) {
		var pipeline = CrossViewPipelines.anaglyphEyeZero(CrossViewConfig.anaglyphType);

		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "CrossView anaglyph composite", main.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform(
					"InSampler",
					eyeZeroCopy.getColorTextureView(),
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
			);
			pass.draw(3, 1, 0, 0);
		}
	}
}
