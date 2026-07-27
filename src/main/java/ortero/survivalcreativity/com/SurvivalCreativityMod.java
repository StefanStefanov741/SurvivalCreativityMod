package ortero.survivalcreativity.com;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ortero.survivalcreativity.com.network.ModNetworking;

public class SurvivalCreativityMod implements ModInitializer {
	public static final String MOD_ID = "survivalcreativitymod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModNetworking.register();
		LOGGER.info("SurvivalCreativityMod loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
