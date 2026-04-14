package com.example.blockcleaner;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ModScreenHandlers {
    public static final ScreenHandlerType<CleanerScreenHandler> CLEANER_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(BlockCleanerMod.MOD_ID, "cleaner_screen"),
            new ScreenHandlerType<>(CleanerScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
    );

    private ModScreenHandlers() {
    }

    public static void register() {
    }
}
