package ortero.survivalcreativity.com.network;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;

import ortero.survivalcreativity.com.SurvivalCreativityMod;

/** Client → server: share an imagination with everyone else who has the mod. */
public record ShareImaginationC2SPayload(
	UUID shareId,
	String imaginationName,
	CompoundTag data
) implements CustomPacketPayload {
	public static final Type<ShareImaginationC2SPayload> TYPE =
		new Type<>(SurvivalCreativityMod.id("share_imagination_c2s"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ShareImaginationC2SPayload> CODEC =
		StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, ShareImaginationC2SPayload::shareId,
			ByteBufCodecs.STRING_UTF8, ShareImaginationC2SPayload::imaginationName,
			ByteBufCodecs.COMPOUND_TAG, ShareImaginationC2SPayload::data,
			ShareImaginationC2SPayload::new
		);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
