package com.example.blockcleaner.client;

import com.example.blockcleaner.CleanerScreenHandler;
import com.example.blockcleaner.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ModNetworkingClient {
    private ModNetworkingClient() {
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SyncBlacklistPayload.ID, (payload, context) -> {
            Set<Integer> rawIds = decodeRawIds(payload.serializedIds());
            context.client().execute(() -> {
                if (context.client().player == null) {
                    return;
                }
                if (!(context.client().player.currentScreenHandler instanceof CleanerScreenHandler screenHandler)) {
                    return;
                }
                if (screenHandler.syncId != payload.syncId()) {
                    return;
                }
                screenHandler.setSyncedBlacklistRawIds(rawIds);
            });
        });
    }

    private static Set<Integer> decodeRawIds(String csv) {
        Set<Integer> result = new HashSet<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Identifier::tryParse)
                .filter(id -> id != null && Registries.ITEM.containsId(id))
                .map(Registries.ITEM::get)
                .mapToInt(Registries.ITEM::getRawId)
                .filter(rawId -> rawId >= 0)
                .forEach(result::add);
        return result;
    }
}
