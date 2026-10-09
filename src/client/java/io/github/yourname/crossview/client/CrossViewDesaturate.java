package io.github.arturopakastur.crossview.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.nio.ByteBuffer;
import java.util.Optional;
import io.github.arturopakastur.crossview.CrossView;
import org.lwjgl.system.MemoryStack;

/**
 * The "Desaturate" option (0-100).
 *
 * The original mod baked this into every loaded texture, the sky/fog/biome colors and sheep wool,
 * then reloaded all resources whenever the slider moved. Here it is one full-screen pass over the
 * rendered world, run on each eye's image BEFORE the anaglyph channels are combined. That means
 * it reacts instantly and needs no resource reload.
 *
 * Formula (same as the original matrix): result = lerp(color, average(R, G, B), percent / 100).
 */
public final class CrossViewDesaturate {
	private CrossViewDesaturate() {
	}

	private static final int UBO_SIZE = new Std140SizeCalculator().putFloat().get();

	private static TextureTarget scratch;
	private static GpuBuffer amountBuffer;
	/** If the pass fails, wait this long before trying again (it is never given up for the session). */
	private static final long RETRY_DELAY_NANOS = 2_000_000_000L;

	private static int failures;
	private static boolean waitingToRetry;
	private static long retryAtNanos;

	/** Desaturates the main target's color in place. Does nothing at 0 percent. */
	public static void apply(RenderTarget main) {
		int percent = CrossViewConfig.desaturate;
		if (percent <= 0) {
			return;
		}

		if (waitingToRetry) {
			if (System.nanoTime() - retryAtNanos < 0) {
				return;
			}
			waitingToRetry = false;
		}

		try {
			applyInternal(main, percent);
			failures = 0;
		} catch (RuntimeException e) {
			// Most likely a shader problem. Don't crash the game: log it and try again a bit later.
			failures++;
			waitingToRetry = true;
			retryAtNanos = System.nanoTime() + RETRY_DELAY_NANOS;
			if (failures <= 3) {
				CrossView.LOGGER.error("CrossView desaturation failed (attempt {}); will retry shortly", failures, e);
			}
		}
	}

	private static void applyInternal(RenderTarget main, int percent) {
		if (scratch == null) {
			scratch = new TextureTarget("CrossView desaturate", main.width, main.height, GpuFormat.RGBA8_UNORM, null);
		} else if (scratch.width != main.width || scratch.height != main.height) {
			scratch.resize(main.width, main.height);
		}

		if (amountBuffer == null) {
			amountBuffer = RenderSystem.getDevice().createBuffer(() -> "CrossView desaturate amount", 136, UBO_SIZE);
		}

		// A texture can't be read and written in the same pass, so copy the image aside first.
		scratch.copyColorFrom(main);

		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer data = Std140Builder.onStack(stack, UBO_SIZE).putFloat(percent / 100.0F).get();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(amountBuffer.slice(), data);
		}

		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "CrossView desaturate", main.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(CrossViewPipelines.DESATURATE));
			pass.setUniform(
					"InSampler",
					scratch.getColorTextureView(),
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
			);
			pass.setUniform(CrossViewPipelines.DESATURATE_UNIFORM, amountBuffer);
			pass.draw(3, 1, 0, 0);
		}
	}
}
