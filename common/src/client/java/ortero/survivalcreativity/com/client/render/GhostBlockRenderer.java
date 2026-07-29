package ortero.survivalcreativity.com.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import ortero.survivalcreativity.com.SurvivalCreativityMod;
import ortero.survivalcreativity.com.client.imagination.BlockChange;
import ortero.survivalcreativity.com.client.imagination.HologramSettings;
import ortero.survivalcreativity.com.client.imagination.Imagination;
import ortero.survivalcreativity.com.client.imagination.ImaginationFluids;
import ortero.survivalcreativity.com.client.imagination.ImaginationManager;

/**
 * Renders hologram overlays only while previewing a saved imagination in survival.
 */
public final class GhostBlockRenderer {
	private static final Logger LOGGER = SurvivalCreativityMod.LOGGER;
	private static final int GHOST_RGB = 0x60C8FF;
	private static final int BREAK_RGB = 0xFF5555;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int[] NO_TINT_LAYERS = new int[0];
	private static final double RENDER_DISTANCE_SQ = 96.0 * 96.0;

	private GhostBlockRenderer() {
	}

	private static int hologramTint() {
		return ARGB.color(HologramSettings.opacityByte(), 255, 255, 255);
	}

	private static int ghostOutline() {
		return colorWithOpacity(GHOST_RGB);
	}

	private static int breakOutline() {
		return colorWithOpacity(BREAK_RGB);
	}

	private static int colorWithOpacity(int rgb) {
		return ARGB.color(HologramSettings.opacityByte(), (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
	}

	private static int fluidAlpha() {
		return HologramSettings.opacityByte();
	}

	public static void renderHologramOverlays(SubmitNodeCollector collector, PoseStack poseStack, LevelRenderState levelState) {
		ImaginationManager manager = ImaginationManager.INSTANCE;
		if (!manager.isPreviewing()) {
			return;
		}

		Imagination overlay = manager.preview();
		if (overlay == null || overlay.isEmpty()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null) {
			return;
		}

		CameraRenderState cameraState = levelState.cameraRenderState;
		Vec3 camera = cameraState.pos;

		HologramBlockGetter hologramBlocks = new HologramBlockGetter(level, overlay, manager);

		for (var entry : overlay.changes().entrySet()) {
			BlockPos pos = entry.getKey();
			if (manager.isSuppressed(pos) || !HologramSettings.isInSlice(pos)) {
				continue;
			}
			if (pos.distToCenterSqr(camera.x, camera.y, camera.z) > RENDER_DISTANCE_SQ) {
				continue;
			}

			BlockChange change = entry.getValue();
			BlockState real = level.getBlockState(pos);
			if (isChangeSatisfied(change, real)) {
				continue;
			}
			if (change.placement()) {
				if (isObstructedByOriginal(change, real)) {
					if (!HologramSettings.hideBreaks()) {
						submitBreakMarker(collector, poseStack, camera, pos, real);
					}
				} else if (!HologramSettings.hidePlacements()) {
					submitGhostPlacement(client, collector, poseStack, level, hologramBlocks, overlay, manager, camera, pos, change.imaginedState());
				}
			} else if (!HologramSettings.hideBreaks()) {
				submitBreakMarker(collector, poseStack, camera, pos, real);
			}
		}

		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
		for (ImaginationManager.GhostEntity ghost : manager.ghostEntities()) {
			if (!HologramSettings.isInSlice(ghost.entity().position())) {
				continue;
			}
			if (ghost.placement() ? HologramSettings.hidePlacements() : HologramSettings.hideBreaks()) {
				continue;
			}
			submitGhostEntity(dispatcher, collector, poseStack, cameraState, camera, ghost);
		}
	}

	/** Placement done when the live block matches; break done when the spot is empty. */
	private static boolean isChangeSatisfied(BlockChange change, BlockState real) {
		if (change.placement()) {
			return real.equals(change.imaginedState());
		}
		return real.isAir();
	}

	/**
	 * True when the live block is still the hologram's recorded original (and that original
	 * isn't already the desired placement). Only that preexisting block gets the red cue.
	 */
	private static boolean isObstructedByOriginal(BlockChange change, BlockState real) {
		BlockState original = change.originalState();
		if (original.isAir() || real.isAir()) {
			return false;
		}
		if (real.equals(change.imaginedState())) {
			return false;
		}
		return real.equals(original);
	}

	private static void submitGhostPlacement(
		Minecraft client,
		SubmitNodeCollector collector,
		PoseStack poseStack,
		ClientLevel level,
		HologramBlockGetter hologramBlocks,
		Imagination overlay,
		ImaginationManager manager,
		Vec3 camera,
		BlockPos pos,
		BlockState state
	) {
		FluidState fluid = ImaginationFluids.asStill(state.getFluidState());
		if (!fluid.isEmpty()) {
			submitGhostFluid(client, collector, poseStack, level, hologramBlocks, camera, pos, fluid);
		}

		VoxelShape shape = state.getShape(level, pos, CollisionContext.empty());
		if (!state.isAir() && (fluid.isEmpty() || !shape.isEmpty())) {
			submitGhostSolid(client, collector, poseStack, level, overlay, manager, camera, pos, state);
		}
	}

	private static void submitGhostSolid(
		Minecraft client,
		SubmitNodeCollector collector,
		PoseStack poseStack,
		ClientLevel level,
		Imagination overlay,
		ImaginationManager manager,
		Vec3 camera,
		BlockPos pos,
		BlockState state
	) {
		var model = client.getModelManager().getBlockStateModelSet().get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(pos.asLong()), parts);
		if (parts.isEmpty()) {
			return;
		}

		int[] tintLayers = resolveTintLayers(client, state, level, pos);

		poseStack.pushPose();
		poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
		poseStack.translate(0.02, 0.02, 0.02);
		poseStack.scale(0.96f, 0.96f, 0.96f);

		collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, consumer) -> {
			QuadInstance instance = new QuadInstance();
			instance.setLightCoords(FULL_BRIGHT);
			instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
			for (BlockStateModelPart part : parts) {
				for (Direction direction : Direction.values()) {
					if (shouldCullGhostFace(level, overlay, manager, pos.relative(direction))) {
						continue;
					}
					for (BakedQuad quad : part.getQuads(direction)) {
						putGhostQuad(pose, consumer, instance, quad, tintLayers);
					}
				}
				for (BakedQuad quad : part.getQuads(null)) {
					putGhostQuad(pose, consumer, instance, quad, tintLayers);
				}
			}
		});

		VoxelShape shape = state.getShape(level, pos, CollisionContext.empty());
		if (!shape.isEmpty()) {
			collector.submitShapeOutline(poseStack, shape, RenderTypes.linesTranslucent(), ghostOutline(), 1.5f, false);
		}
		poseStack.popPose();
	}

	private static boolean shouldCullGhostFace(
		ClientLevel level,
		Imagination overlay,
		ImaginationManager manager,
		BlockPos neighbor
	) {
		BlockState neighborReal = level.getBlockState(neighbor);
		if (manager.isSuppressed(neighbor) || (overlay.get(neighbor) != null
			&& overlay.get(neighbor).placement()
			&& isChangeSatisfied(overlay.get(neighbor), neighborReal))) {
			return !neighborReal.isAir();
		}
		return !neighborReal.isAir();
	}

	private static void putGhostQuad(
		PoseStack.Pose pose,
		VertexConsumer consumer,
		QuadInstance instance,
		BakedQuad quad,
		int[] tintLayers
	) {
		int tintIndex = quad.materialInfo().tintIndex();
		int color = hologramTint();
		if (tintIndex != -1 && tintIndex < tintLayers.length) {
			color = ARGB.multiply(hologramTint(), tintLayers[tintIndex]);
		}
		instance.setColor(color);
		consumer.putBakedQuad(pose, quad, instance);
	}

	/** Biome/foliage/grass tints so holograms aren't greyscale; alpha forced opaque then multiplied by hologram tint. */
	private static int[] resolveTintLayers(Minecraft client, BlockState state, ClientLevel level, BlockPos pos) {
		List<BlockTintSource> sources = client.getBlockColors().getTintSources(state);
		if (sources.isEmpty()) {
			return NO_TINT_LAYERS;
		}
		int[] layers = new int[sources.size()];
		for (int i = 0; i < sources.size(); i++) {
			layers[i] = ARGB.opaque(sources.get(i).colorInWorld(state, level, pos));
		}
		return layers;
	}

	private static void submitGhostFluid(
		Minecraft client,
		SubmitNodeCollector collector,
		PoseStack poseStack,
		ClientLevel level,
		HologramBlockGetter hologramBlocks,
		Vec3 camera,
		BlockPos pos,
		FluidState fluid
	) {
		FluidModel model = client.getModelManager().getFluidStateModelSet().get(fluid);
		if (model == null || model.stillMaterial() == null || model.flowingMaterial() == null) {
			return;
		}
		TextureAtlasSprite still = model.stillMaterial().sprite();
		TextureAtlasSprite flowing = model.flowingMaterial().sprite();
		if (still == null || flowing == null) {
			return;
		}

		float height = Math.max(fluid.getOwnHeight(), 0.8888889f);
		int tint = fluidTint(model, level, pos, fluid);
		int r = (tint >> 16) & 0xFF;
		int g = (tint >> 8) & 0xFF;
		int b = tint & 0xFF;

		boolean up = hologramBlocks.getFluidState(pos.above()).isEmpty();
		boolean down = shouldDrawFluidFace(hologramBlocks, pos, Direction.DOWN, fluid);
		boolean north = shouldDrawFluidFace(hologramBlocks, pos, Direction.NORTH, fluid);
		boolean south = shouldDrawFluidFace(hologramBlocks, pos, Direction.SOUTH, fluid);
		boolean west = shouldDrawFluidFace(hologramBlocks, pos, Direction.WEST, fluid);
		boolean east = shouldDrawFluidFace(hologramBlocks, pos, Direction.EAST, fluid);

		float h = height;
		float u0 = still.getU(0.0f);
		float u1 = still.getU(1.0f);
		float v0 = still.getV(0.0f);
		float v1 = still.getV(1.0f);
		float fu0 = flowing.getU(0.0f);
		float fu1 = flowing.getU(1.0f);
		float fv0 = flowing.getV(0.0f);
		float fv1 = flowing.getV(h);

		poseStack.pushPose();
		poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

		int fr = r, fg = g, fb = b;
		int fa = fluidAlpha();
		collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, consumer) -> {
			if (up) {
				quad(pose, consumer, 0, h, 0, 1, h, 0, 1, h, 1, 0, h, 1, u0, v0, u1, v0, u1, v1, u0, v1, fr, fg, fb, fa, 0, 1, 0);
			}
			if (down) {
				quad(pose, consumer, 0, 0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0, u0, v1, u1, v1, u1, v0, u0, v0, fr, fg, fb, fa, 0, -1, 0);
			}
			if (north) {
				quad(pose, consumer, 1, h, 0, 0, h, 0, 0, 0, 0, 1, 0, 0, fu0, fv0, fu1, fv0, fu1, fv1, fu0, fv1, fr, fg, fb, fa, 0, 0, -1);
			}
			if (south) {
				quad(pose, consumer, 0, h, 1, 1, h, 1, 1, 0, 1, 0, 0, 1, fu0, fv0, fu1, fv0, fu1, fv1, fu0, fv1, fr, fg, fb, fa, 0, 0, 1);
			}
			if (west) {
				quad(pose, consumer, 0, h, 0, 0, h, 1, 0, 0, 1, 0, 0, 0, fu0, fv0, fu1, fv0, fu1, fv1, fu0, fv1, fr, fg, fb, fa, -1, 0, 0);
			}
			if (east) {
				quad(pose, consumer, 1, h, 1, 1, h, 0, 1, 0, 0, 1, 0, 1, fu0, fv0, fu1, fv0, fu1, fv1, fu0, fv1, fr, fg, fb, fa, 1, 0, 0);
			}
		});

		poseStack.popPose();
	}

	private static int fluidTint(FluidModel model, ClientLevel level, BlockPos pos, FluidState fluid) {
		var tintSource = model.tintSource();
		if (tintSource != null) {
			try {
				return tintSource.colorInWorld(fluid.createLegacyBlock(), level, pos);
			} catch (Exception ignored) {
			}
		}
		if (fluid.is(net.minecraft.tags.FluidTags.LAVA)) {
			return 0xFFFFFF;
		}
		if (fluid.is(net.minecraft.tags.FluidTags.WATER)) {
			return net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(level, pos);
		}
		return 0xFFFFFF;
	}

	private static boolean shouldDrawFluidFace(HologramBlockGetter blocks, BlockPos pos, Direction dir, FluidState self) {
		FluidState neighbor = blocks.getFluidState(pos.relative(dir));
		return neighbor.isEmpty() || !neighbor.getType().isSame(self.getType());
	}

	private static void quad(
		PoseStack.Pose pose,
		VertexConsumer consumer,
		float x0, float y0, float z0,
		float x1, float y1, float z1,
		float x2, float y2, float z2,
		float x3, float y3, float z3,
		float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3,
		int r, int g, int b, int a,
		float nx, float ny, float nz
	) {
		int color = (a << 24) | (r << 16) | (g << 8) | b;
		consumer.addVertex(pose, x0, y0, z0).setColor(color).setUv(u0, v0).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
		consumer.addVertex(pose, x1, y1, z1).setColor(color).setUv(u1, v1).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
		consumer.addVertex(pose, x2, y2, z2).setColor(color).setUv(u2, v2).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
		consumer.addVertex(pose, x3, y3, z3).setColor(color).setUv(u3, v3).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
	}

	private static void submitGhostEntity(
		EntityRenderDispatcher dispatcher,
		SubmitNodeCollector collector,
		PoseStack poseStack,
		CameraRenderState cameraState,
		Vec3 camera,
		ImaginationManager.GhostEntity ghost
	) {
		Entity entity = ghost.entity();
		if (entity.distanceToSqr(camera.x, camera.y, camera.z) > RENDER_DISTANCE_SQ) {
			return;
		}
		try {
			EntityRenderState renderState = dispatcher.extractEntity(entity, 0.0f);
			renderState.outlineColor = ghost.placement() ? ghostOutline() : breakOutline();
			dispatcher.submit(
				renderState,
				cameraState,
				renderState.x - camera.x,
				renderState.y - camera.y,
				renderState.z - camera.z,
				poseStack,
				collector
			);
		} catch (Exception e) {
			LOGGER.debug("Failed to render hologram entity {}", entity.getType(), e);
		}
	}

	private static void submitBreakMarker(
		SubmitNodeCollector collector,
		PoseStack poseStack,
		Vec3 camera,
		BlockPos pos,
		BlockState state
	) {
		poseStack.pushPose();
		poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
		VoxelShape shape = state.getShape(Minecraft.getInstance().level, pos, CollisionContext.empty());
		if (!shape.isEmpty()) {
			collector.submitShapeOutline(poseStack, shape, RenderTypes.linesTranslucent(), breakOutline(), 2.5f, false);
			int wash = ARGB.color(Math.min(120, HologramSettings.opacityByte()), 255, 64, 64);
			collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, consumer) -> {
				fillShapeWash(pose, consumer, shape, wash);
			});
		}
		poseStack.popPose();
	}

	private static void fillShapeWash(PoseStack.Pose pose, VertexConsumer consumer, VoxelShape shape, int color) {
		shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
			float fx0 = (float) x0;
			float fy0 = (float) y0;
			float fz0 = (float) z0;
			float fx1 = (float) x1;
			float fy1 = (float) y1;
			float fz1 = (float) z1;
			quadFlat(pose, consumer, fx0, fy1, fz0, fx1, fy1, fz0, fx1, fy1, fz1, fx0, fy1, fz1, color, 0, 1, 0);
			quadFlat(pose, consumer, fx0, fy0, fz1, fx1, fy0, fz1, fx1, fy0, fz0, fx0, fy0, fz0, color, 0, -1, 0);
			quadFlat(pose, consumer, fx0, fy1, fz0, fx0, fy1, fz1, fx0, fy0, fz1, fx0, fy0, fz0, color, -1, 0, 0);
			quadFlat(pose, consumer, fx1, fy1, fz1, fx1, fy1, fz0, fx1, fy0, fz0, fx1, fy0, fz1, color, 1, 0, 0);
			quadFlat(pose, consumer, fx0, fy1, fz1, fx1, fy1, fz1, fx1, fy0, fz1, fx0, fy0, fz1, color, 0, 0, 1);
			quadFlat(pose, consumer, fx1, fy1, fz0, fx0, fy1, fz0, fx0, fy0, fz0, fx1, fy0, fz0, color, 0, 0, -1);
		});
	}

	private static void quadFlat(
		PoseStack.Pose pose,
		VertexConsumer consumer,
		float x0, float y0, float z0,
		float x1, float y1, float z1,
		float x2, float y2, float z2,
		float x3, float y3, float z3,
		int color,
		float nx, float ny, float nz
	) {
		consumer.addVertex(pose, x0, y0, z0).setColor(color).setUv(0, 0).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
		consumer.addVertex(pose, x1, y1, z1).setColor(color).setUv(1, 0).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
		consumer.addVertex(pose, x2, y2, z2).setColor(color).setUv(1, 1).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
		consumer.addVertex(pose, x3, y3, z3).setColor(color).setUv(0, 1).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
	}
}
