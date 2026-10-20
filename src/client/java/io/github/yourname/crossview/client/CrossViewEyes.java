package io.github.arturopakastur.crossview.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.gizmos.SimpleGizmoCollector.GizmoInstance;
import org.joml.Matrix4f;

/**
 * Pieces shared by Anaglyph and Side By Side.
 *
 * IMPORTANT: the eye shift goes into the VIEW MATRIX, never into camera.pos. The level renderer
 * re-centers its chunk grid on camera.pos, so moving it across a 16-block boundary crashes
 * (queued chunk updates point at sections that no longer exist) and thrashes chunk visibility.
 */
public final class CrossViewEyes {
	private CrossViewEyes() {
	}

	/**
	 * The level renderer empties these lists while drawing, so a second eye needs copies.
	 * {@code gizmos} are the F3 debug shapes (hitboxes, chunk borders, ...), see CrossViewGizmos.
	 */
	public record Snapshot(
			List<EntityRenderState> entities,
			List<BlockEntityRenderState> blockEntities,
			List<BlockBreakingRenderState> blockBreaking,
			List<GizmoInstance> gizmos
	) {
		public static Snapshot take(LevelRenderState level) {
			return new Snapshot(
					new ArrayList<>(level.entityRenderStates),
					new ArrayList<>(level.blockEntityRenderStates),
					new ArrayList<>(level.blockBreakingRenderStates),
					CrossViewGizmos.takeCaptured()
			);
		}

		public void restoreInto(LevelRenderState level) {
			level.entityRenderStates.clear();
			level.entityRenderStates.addAll(entities);
			level.blockEntityRenderStates.clear();
			level.blockEntityRenderStates.addAll(blockEntities);
			level.blockBreakingRenderStates.clear();
			level.blockBreakingRenderStates.addAll(blockBreaking);
			CrossViewGizmos.restore(gizmos);
		}
	}

	/**
	 * Shifts the view (and, for Legacy 3D, the projection) for one eye.
	 * Eye 0 and eye 1 are shifted in opposite directions; "Invert 3D" swaps them.
	 */
	public static void applyEye(CameraRenderState camera, Matrix4f baseView, Matrix4f baseProjection, int eye) {
		// Original: offset = (invert ? 0.02 : -0.02) * power.
		double amount = (CrossViewConfig.invert ? 0.02 : -0.02) * CrossViewConfig.power * (eye * 2 - 1);

		// Uses the CAMERA's yaw (not the player's), so third-person views keep the eyes the right way
		// round. The original used the player's yaw, which swapped the eyes in front-facing third person.
		double yaw = Math.toRadians(camera.yRot);

		// Original: translate the pose stack by (cos(yaw), 0, sin(yaw)) * amount, in world axes, after
		// the camera rotation. For a matrix that is "view * translate".
		camera.viewRotationMatrix.set(baseView);
		camera.viewRotationMatrix.translate((float) (Math.cos(yaw) * amount), 0.0F, (float) (Math.sin(yaw) * amount));

		camera.projectionMatrix.set(baseProjection);
		if (CrossViewConfig.legacy3d) {
			// Original "Legacy 3D": shift the projected image sideways instead of relying on parallax alone.
			double shift = -(eye * 2 - 1) * 0.07 * (CrossViewConfig.invert ? -1 : 1) * CrossViewConfig.power / 5.0;
			camera.projectionMatrix.translateLocal((float) shift, 0.0F, 0.0F);
		}
	}
}
