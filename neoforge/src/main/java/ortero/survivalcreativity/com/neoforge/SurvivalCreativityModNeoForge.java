package ortero.survivalcreativity.com.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import ortero.survivalcreativity.com.SurvivalCreativityMod;
import ortero.survivalcreativity.com.neoforge.network.ModNetworkingNeoForge;

@Mod(SurvivalCreativityMod.MOD_ID)
public class SurvivalCreativityModNeoForge {
	public SurvivalCreativityModNeoForge(IEventBus modBus) {
		ModNetworkingNeoForge.register(modBus);
		SurvivalCreativityMod.LOGGER.info("SurvivalCreativityMod (NeoForge) loaded");
	}
}
