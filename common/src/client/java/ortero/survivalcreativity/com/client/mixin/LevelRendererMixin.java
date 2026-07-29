package ortero.survivalcreativity.com.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ortero.survivalcreativity.com.client.render.GhostBlockRenderer;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	@Inject(method = "submitEntities", at = @At("RETURN"))
	private void survivalcreativity$afterSubmitEntities(
		PoseStack poseStack,
		LevelRenderState levelRenderState,
		SubmitNodeCollector collector,
		CallbackInfo ci
	) {
		GhostBlockRenderer.renderHologramOverlays(collector, poseStack, levelRenderState);
	}
}
