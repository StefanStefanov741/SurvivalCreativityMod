package ortero.survivalcreativity.com.network;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;

import ortero.survivalcreativity.com.SurvivalCreativityMod;

/** Server → clients: announce a shared imagination and deliver its data. */
public record ShareImaginationS2CPayload(
	UUID shareId,
	String senderName,
	String imaginationName,
	CompoundTag data
) implements CustomPacketPayload {
	public static final Type<ShareImaginationS2CPayload> TYPE =
		new Type<>(SurvivalCreativityMod.id("share_imagination_s2c"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ShareImaginationS2CPayload> CODEC =
		StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, ShareImaginationS2CPayload::shareId,
			ByteBufCodecs.STRING_UTF8, ShareImaginationS2CPayload::senderName,
			ByteBufCodecs.STRING_UTF8, ShareImaginationS2CPayload::imaginationName,
			ByteBufCodecs.COMPOUND_TAG, ShareImaginationS2CPayload::data,
			ShareImaginationS2CPayload::new
		);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
