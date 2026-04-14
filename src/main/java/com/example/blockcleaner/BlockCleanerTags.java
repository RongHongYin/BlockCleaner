package com.example.blockcleaner;

import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class BlockCleanerTags {
    public static final TagKey<Item> PICKAXE_TOOLS = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", "pickaxes"));
    public static final TagKey<Item> AXE_TOOLS = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", "axes"));
    public static final TagKey<Item> SHOVEL_TOOLS = TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", "shovels"));

    private BlockCleanerTags() {
    }
}
