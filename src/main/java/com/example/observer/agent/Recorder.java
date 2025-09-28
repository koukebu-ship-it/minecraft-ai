package com.example.observer.agent;

import com.example.observer.ObserverMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Mod.EventBusSubscriber(modid = ObserverMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Recorder {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Path DIR  = FMLPaths.GAMEDIR.get().resolve("agent");
    private static final Path FILE = DIR.resolve("events.json"); // 注意：是 events.json（非 .jsonl）

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        MinecraftServer server = e.getServer();
        if (server == null) return;

        try { Files.createDirectories(DIR); } catch (Exception ignored) {}

        ServerLevel level = server.overworld();
        if (level == null) return;

        ServerPlayer player = server.getPlayerList().getPlayers().isEmpty()
                ? null : server.getPlayerList().getPlayers().get(0);

        long tick = level.getGameTime();

        BlockPos basePos = (player != null) ? player.blockPosition() : level.getSharedSpawnPos();
        BlockPos samplePos = basePos.above();

        int skyLight   = level.getBrightness(LightLayer.SKY, samplePos);
        int blockLight = level.getBrightness(LightLayer.BLOCK, samplePos);
        boolean canSeeSky = level.canSeeSky(samplePos);

        int hostiles = level.getEntitiesOfClass(Monster.class, new AABB(basePos).inflate(16)).size();

        JsonObject j = new JsonObject();
        j.addProperty("tick", tick);
        j.addProperty("isDay", level.isDay());

        j.addProperty("x", basePos.getX());
        j.addProperty("y", basePos.getY());
        j.addProperty("z", basePos.getZ());

        j.addProperty("skyLight", skyLight);
        j.addProperty("blockLight", blockLight);
        j.addProperty("canSeeSky", canSeeSky);

        if (player != null) {
            j.addProperty("hp", player.getHealth());     // 0..20
            j.addProperty("maxHp", player.getMaxHealth());
            FoodData food = player.getFoodData();
            j.addProperty("food", food.getFoodLevel());  // 0..20
            j.addProperty("saturation", food.getSaturationLevel());
            j.addProperty("air", player.getAirSupply()); // 0 会窒息

        }

        j.addProperty("hostiles", hostiles);

        try (OutputStream out = Files.newOutputStream(FILE,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            out.write((GSON.toJson(j) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
}
