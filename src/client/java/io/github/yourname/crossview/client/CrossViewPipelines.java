package io.github.arturopakastur.crossview.client;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.Optional;
import io.github.arturopakastur.crossview.CrossView;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * All render pipelines used by CrossView.
 *
 * 1) Anaglyph: plain full-screen copies (vanilla's own blit shaders) that only write some color
 *    channels. This is the 26.3 equivalent of the original mod's glColorMask(...) calls.
 *    Anaglyph type 0 = Red-Cyan, 1 = Green-Magenta, 2 = Blue-Yellow. Eye 1 owns the channel with
 *    that index; eye 0 owns the other two. Channel bits: red = 1, green = 2, blue = 4.
 *
 * 2) Desaturate: vanilla's screen quad plus our own fragment shader
 *    (assets/crossview/shaders/core/desaturate.fsh) with one uniform buffer holding the amount.
 *
 * 3) Side By Side compose: vanilla's screen quad plus our own fragment shader
 *    (assets/crossview/shaders/core/sbs_compose.fsh) that puts two eye images into one image
 *    twice as wide. A shader is used because vanilla's OpenGL texture copy cannot write to a
 *    non-zero destination offset, and render passes cannot change the viewport.
 */
public final class CrossViewPipelines {
	private CrossViewPipelines() {
	}

	/** Index = anaglyph type. Each one writes the channels that eye 0 owns. */
	private static final RenderPipeline[] EYE_ZERO = new RenderPipeline[3];

	/** Name of the uniform block in desaturate.fsh. */
	public static final String DESATURATE_UNIFORM = "CrossViewDesaturate";

	public static final RenderPipeline DESATURATE;

	public static final RenderPipeline SBS_COMPOSE;

	static {
		for (int type = 0; type < 3; type++) {
			int mask = ColorTargetState.WRITE_COLOR & ~(1 << type);
			EYE_ZERO[type] = RenderPipelines.register(
					RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
							.withLocation(CrossView.id("pipeline/anaglyph_eye0_" + type))
							.withVertexShader("core/screenquad")
							.withFragmentShader("core/blit_screen")
							.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
							.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
							.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, mask))
							.build()
			);
		}

		BindGroupLayout desaturateLayout = BindGroupLayout.builder()
				.withUniform(DESATURATE_UNIFORM, UniformType.UNIFORM_BUFFER)
				.build();

		SBS_COMPOSE = RenderPipelines.register(
				RenderPipeline.builder()
						.withLocation(CrossView.id("pipeline/sbs_compose"))
						.withVertexShader("core/screenquad")
						.withFragmentShader(CrossView.id("core/sbs_compose"))
						.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
						.withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
						.withColorTargetState(ColorTargetState.DEFAULT)
						.build()
		);

		DESATURATE = RenderPipelines.register(
				RenderPipeline.builder()
						.withLocation(CrossView.id("pipeline/desaturate"))
						.withVertexShader("core/screenquad")
						.withFragmentShader(CrossView.id("core/desaturate"))
						.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
						.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
						.withBindGroupLayout(desaturateLayout)
						.withColorTargetState(ColorTargetState.DEFAULT)
						.build()
		);
	}

	public static RenderPipeline anaglyphEyeZero(CrossViewConfig.AnaglyphType type) {
		return EYE_ZERO[type.ordinal()];
	}
}
