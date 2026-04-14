package com.example.blockcleaner.client;

import com.example.blockcleaner.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class BlockCleanerClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.CLEANER_SCREEN_HANDLER, CleanerScreen::new);
    }
}
