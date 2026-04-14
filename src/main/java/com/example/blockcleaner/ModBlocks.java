package com.example.blockcleaner;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModBlocks {
    public static final Block CLEANER_BLOCK = registerBlock(
            "cleaner_block",
            settings("cleaner_block").strength(50.0F, 1200.0F).requiresTool()
    );

    private ModBlocks() {
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(CLEANER_BLOCK));
        BlockCleanerMod.LOGGER.info("Registered mod blocks.");
    }

    private static AbstractBlock.Settings settings(String id) {
        Identifier identifier = Identifier.of(BlockCleanerMod.MOD_ID, id);
        RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, identifier);
        return AbstractBlock.Settings.create().registryKey(key);
    }

    private static Block registerBlock(String id, AbstractBlock.Settings settings) {
        Block block = new CleanerBlock(settings);
        Identifier identifier = Identifier.of(BlockCleanerMod.MOD_ID, id);
        Registry.register(Registries.BLOCK, identifier, block);
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, identifier);
        Registry.register(Registries.ITEM, identifier, new BlockItem(block, new Item.Settings().registryKey(itemKey)));
        return block;
    }
}
