package com.example.observer.agent;

import com.example.observer.ObserverMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = ObserverMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandFileRunner {
    private static final Path DIR  = FMLPaths.GAMEDIR.get().resolve("agent");
    private static final Path FILE = DIR.resolve("commands.jsonl");
    private static long lastSize = 0;

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        try { Files.createDirectories(DIR); } catch (Exception ignored) {}
        if (!Files.exists(FILE)) return;

        try (var raf = new RandomAccessFile(FILE.toFile(), "r")) {
            long len = raf.length();
            if (len <= lastSize) return;
            raf.seek(lastSize);
            String line;
            while ((line = raf.readLine()) != null) {
                handleLine(e.getServer(), new String(line.getBytes("ISO-8859-1")));
            }
            lastSize = raf.getFilePointer();
        } catch (Exception ignored) {}
    }

    private static void handleLine(MinecraftServer server, String line) {
        try {
            JsonObject j = JsonParser.parseString(line).getAsJsonObject();
            String op = j.get("op").getAsString();
            ServerLevel level = server.overworld();

            switch (op) {
                case "place" -> {
                    String id = j.get("block").getAsString();
                    BlockState state = BuiltInRegistries.BLOCK.get(new ResourceLocation(id)).defaultBlockState();
                    BlockPos pos = new BlockPos(j.get("x").getAsInt(), j.get("y").getAsInt(), j.get("z").getAsInt());
                    level.setBlock(pos, state, 3);
                }
                case "break" -> {
                    BlockPos pos = new BlockPos(j.get("x").getAsInt(), j.get("y").getAsInt(), j.get("z").getAsInt());
                    level.destroyBlock(pos, true);
                }
                case "kill" -> {
                    int r = j.get("radius").getAsInt();
                    AABB box = new AABB(level.getSharedSpawnPos()).inflate(r);
                    for (LivingEntity ent : level.getEntitiesOfClass(LivingEntity.class, box,
                            e -> !(e instanceof ServerPlayer))) {
                        ent.remove(Entity.RemovalReason.KILLED);
                    }
                }
                case "gamerule" -> {
                    boolean v = j.get("value").getAsBoolean();
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(v, server);
                }
            }
        } catch (Exception ignored) {}
    }
}
