package com.example.blockcleaner;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;

public class CleanerScreenHandler extends ScreenHandler {
    private final CleanerBlockEntity blockEntity;
    private final PropertyDelegate properties;
    private Set<Integer> syncedBlacklistRawIds = new HashSet<>();

    public CleanerScreenHandler(int syncId, PlayerInventory inventory) {
        this(syncId, inventory, null, new ArrayPropertyDelegate(8));
    }

    public CleanerScreenHandler(int syncId, PlayerInventory inventory, CleanerBlockEntity blockEntity, PropertyDelegate properties) {
        super(ModScreenHandlers.CLEANER_SCREEN_HANDLER, syncId);
        this.blockEntity = blockEntity;
        this.properties = properties;
        this.addProperties(properties);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (blockEntity == null) {
            return false;
        }
        if (id >= 1000 && id <= 1099) {
            blockEntity.setRangeChunks(id - 1000);
            return true;
        }
        if (id >= 2000 && id <= 12000) {
            blockEntity.setSpeedPerSecond(id - 2000);
            return true;
        }
        if (id >= 30000 && id <= 34096) {
            blockEntity.setTargetY(id - 30000 - 1024);
            return true;
        }
        if (id >= CleanerBlockEntity.ACTION_ADD_BLACKLIST_BASE
                && id < CleanerBlockEntity.ACTION_REMOVE_BLACKLIST_BASE) {
            int rawId = id - CleanerBlockEntity.ACTION_ADD_BLACKLIST_BASE;
            Item item = Registries.ITEM.get(rawId);
            if (item != null) {
                blockEntity.addDropBlacklistItem(item);
                sendBlacklistSyncTo(player);
            }
            return true;
        }
        if (id >= CleanerBlockEntity.ACTION_REMOVE_BLACKLIST_BASE) {
            int rawId = id - CleanerBlockEntity.ACTION_REMOVE_BLACKLIST_BASE;
            Item item = Registries.ITEM.get(rawId);
            if (item != null) {
                blockEntity.removeDropBlacklistItem(item);
                sendBlacklistSyncTo(player);
            }
            return true;
        }
        blockEntity.applyAction(id);
        return true;
    }

    public int getMode() {
        return properties.get(0);
    }

    public int getDirection() {
        return properties.get(1);
    }

    public int getRangeChunks() {
        return properties.get(2);
    }

    public int getSpeedPerSecond() {
        return properties.get(4);
    }

    public boolean isActive() {
        return properties.get(5) == 1;
    }

    public int getSpeedMode() {
        return properties.get(6);
    }

    public int getTargetY() {
        return properties.get(3);
    }

    public boolean shouldKeepOneDurability() {
        return properties.get(7) == 1;
    }

    public Set<Integer> getSyncedBlacklistRawIds() {
        return new HashSet<>(syncedBlacklistRawIds);
    }

    public void setSyncedBlacklistRawIds(Set<Integer> rawIds) {
        this.syncedBlacklistRawIds = new HashSet<>(rawIds);
    }

    public CleanerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    private void sendBlacklistSyncTo(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ModNetworking.sendBlacklistSync(serverPlayer, this);
        }
    }
}
