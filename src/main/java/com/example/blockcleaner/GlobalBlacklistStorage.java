package com.example.blockcleaner;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class GlobalBlacklistStorage {
    private static final String FILE_NAME = "blockcleaner-global-blacklist.txt";
    private static Path loadedFromRoot;
    private static final Set<Identifier> blacklist = new HashSet<>();
    private static boolean loaded;

    private GlobalBlacklistStorage() {
    }

    public static synchronized boolean contains(MinecraftServer server, Identifier id) {
        if (id == null) {
            return false;
        }
        loadIfNeeded(server);
        return blacklist.contains(id);
    }

    public static synchronized boolean add(MinecraftServer server, Identifier id) {
        if (id == null) {
            return false;
        }
        loadIfNeeded(server);
        boolean changed = blacklist.add(id);
        if (changed) {
            save(server);
        }
        return changed;
    }

    public static synchronized boolean remove(MinecraftServer server, Identifier id) {
        if (id == null) {
            return false;
        }
        loadIfNeeded(server);
        boolean changed = blacklist.remove(id);
        if (changed) {
            save(server);
        }
        return changed;
    }

    public static synchronized void addAll(MinecraftServer server, Set<Identifier> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        loadIfNeeded(server);
        if (blacklist.addAll(ids)) {
            save(server);
        }
    }

    public static synchronized String serialize(MinecraftServer server) {
        loadIfNeeded(server);
        List<String> ids = blacklist.stream()
                .map(Identifier::toString)
                .sorted()
                .toList();
        return String.join(",", ids);
    }

    public static synchronized int[] rawIds(MinecraftServer server) {
        loadIfNeeded(server);
        return blacklist.stream()
                .sorted((a, b) -> a.toString().compareTo(b.toString()))
                .map(net.minecraft.registry.Registries.ITEM::get)
                .mapToInt(net.minecraft.registry.Registries.ITEM::getRawId)
                .filter(id -> id >= 0)
                .toArray();
    }

    public static Set<Identifier> parseSerializedIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Identifier::tryParse)
                .filter(id -> id != null && net.minecraft.registry.Registries.ITEM.containsId(id))
                .collect(Collectors.toSet());
    }

    private static void loadIfNeeded(MinecraftServer server) {
        Path root = server.getSavePath(WorldSavePath.ROOT);
        if (loaded && root.equals(loadedFromRoot)) {
            return;
        }

        loadedFromRoot = root;
        loaded = true;
        blacklist.clear();

        Path file = root.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return;
        }

        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            blacklist.addAll(parseSerializedIds(content));
        } catch (IOException e) {
            BlockCleanerMod.LOGGER.warn("Failed to load global blacklist from {}", file, e);
        }
    }

    private static void save(MinecraftServer server) {
        Path root = server.getSavePath(WorldSavePath.ROOT);
        Path file = root.resolve(FILE_NAME);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, serialize(server), StandardCharsets.UTF_8);
        } catch (IOException e) {
            BlockCleanerMod.LOGGER.warn("Failed to save global blacklist to {}", file, e);
        }
    }
}
