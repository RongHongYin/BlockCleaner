package com.example.blockcleaner;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {
    public static final BlockEntityType<CleanerBlockEntity> CLEANER_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(BlockCleanerMod.MOD_ID, "cleaner_block_entity"),
            FabricBlockEntityTypeBuilder.create(CleanerBlockEntity::new, ModBlocks.CLEANER_BLOCK).build()
    );

    private ModBlockEntities() {
    }

    public static void register() {
    }
}
