package io.github.arturopakastur.crossview.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import io.github.arturopakastur.crossview.client.CrossViewSideBySide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	/**
	 * renderFrame() calls gameRenderer.render() once per frame. In Side By Side we run it once
	 * per eye; in every other mode this just runs the original.
	 */
	@WrapOperation(
			method = "renderFrame",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render()V")
	)
	private void crossview$renderEyes(GameRenderer instance, Operation<Void> original) {
		CrossViewSideBySide.render((Minecraft) (Object) this, instance, () -> original.call(instance));
	}

	/**
	 * The swapchain blit copies the main target's color to the window. In Side By Side we hand it
	 * the full-width composite (both eyes) instead.
	 */
	@WrapOperation(
			method = "renderFrame",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;getColorTextureView()Lcom/mojang/renderpearl/api/textures/GpuTextureView;"
			)
	)
	private GpuTextureView crossview$presentView(RenderTarget instance, Operation<GpuTextureView> original) {
		GpuTextureView composite = CrossViewSideBySide.takePresentView();
		return composite != null ? composite : original.call(instance);
	}
}
