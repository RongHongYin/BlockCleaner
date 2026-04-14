package com.example.blockcleaner;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockCleanerMod implements ModInitializer {
    public static final String MOD_ID = "blockcleaner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.register();
        ModBlockEntities.register();
        ModScreenHandlers.register();
        LOGGER.info("BlockCleaner initialized.");
    }
}
