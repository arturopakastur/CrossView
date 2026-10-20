package io.github.arturopakastur.crossview.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.SimpleGizmoCollector.GizmoInstance;

/**
 * Makes "gizmos" (hitboxes, chunk borders, collision boxes, light levels, ... everything the F3
 * debug shapes draw) show up in BOTH eyes.
 *
 * Why they only showed in one eye: during the extract step the game queues this frame's gizmos in
 * LevelRenderer. The first level render EMPTIES that queue (LevelRenderer.finalizeGizmoCollection),
 * so the second eye found nothing to draw. The same thing is already handled for entities, block
 * entities and block breaking (CrossViewEyes.Snapshot); this class does it for gizmos.
 *
 * How: LevelRendererMixin hands us the list the moment the game queues it. The snapshot keeps it,
 * and before the second eye it is queued again through the game's own public method.
 */
public final class CrossViewGizmos {
	private CrossViewGizmos() {
	}

	private static List<GizmoInstance> captured = List.of();
	private static LevelRenderer renderer;
	/** True while we queue the list again ourselves, so that call is not captured a second time. */
	private static boolean restoring;

	/** Called by LevelRendererMixin whenever the game queues this frame's gizmos. */
	public static void capture(LevelRenderer levelRenderer, List<GizmoInstance> gizmos) {
		if (restoring) {
			return;
		}
		if (CrossViewConfig.mode == CrossViewConfig.Mode.OFF) {
			captured = List.of();
			return;
		}

		renderer = levelRenderer;
		captured = new ArrayList<>(gizmos);
	}

	/** Hands over this frame's gizmos (for the snapshot) and forgets them, so a stale list is never reused. */
	public static List<GizmoInstance> takeCaptured() {
		List<GizmoInstance> result = captured;
		captured = List.of();
		return result;
	}

	/** Queues the gizmos again, so the next level render draws them too. */
	public static void restore(List<GizmoInstance> gizmos) {
		if (renderer == null || gizmos.isEmpty()) {
			return;
		}

		restoring = true;
		try {
			renderer.addMainThreadGizmos(gizmos);
		} finally {
			restoring = false;
		}
	}
}
