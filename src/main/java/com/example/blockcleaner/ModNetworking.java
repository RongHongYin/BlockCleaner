package com.example.blockcleaner;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playC2S().register(RequestBlacklistSyncPayload.ID, RequestBlacklistSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncBlacklistPayload.ID, SyncBlacklistPayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(RequestBlacklistSyncPayload.ID, (payload, context) -> {
            if (!(context.player().currentScreenHandler instanceof CleanerScreenHandler screenHandler)) {
                return;
            }
            if (screenHandler.syncId != payload.syncId()) {
                return;
            }
            sendBlacklistSync(context.player(), screenHandler);
        });
    }

    public static void sendBlacklistSync(ServerPlayerEntity player, CleanerScreenHandler handler) {
        CleanerBlockEntity blockEntity = handler.getBlockEntity();
        if (blockEntity == null) {
            return;
        }
        ServerPlayNetworking.send(player, new SyncBlacklistPayload(handler.syncId, blockEntity.serializeDropBlacklistForSync()));
    }

    public record RequestBlacklistSyncPayload(int syncId) implements CustomPayload {
        public static final CustomPayload.Id<RequestBlacklistSyncPayload> ID =
                new CustomPayload.Id<>(Identifier.of(BlockCleanerMod.MOD_ID, "request_blacklist_sync"));
        public static final PacketCodec<RegistryByteBuf, RequestBlacklistSyncPayload> CODEC =
                PacketCodec.tuple(PacketCodecs.VAR_INT, RequestBlacklistSyncPayload::syncId, RequestBlacklistSyncPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SyncBlacklistPayload(int syncId, String serializedIds) implements CustomPayload {
        public static final CustomPayload.Id<SyncBlacklistPayload> ID =
                new CustomPayload.Id<>(Identifier.of(BlockCleanerMod.MOD_ID, "sync_blacklist"));
        public static final PacketCodec<RegistryByteBuf, SyncBlacklistPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_INT, SyncBlacklistPayload::syncId,
                        PacketCodecs.STRING, SyncBlacklistPayload::serializedIds,
                        SyncBlacklistPayload::new
                );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
