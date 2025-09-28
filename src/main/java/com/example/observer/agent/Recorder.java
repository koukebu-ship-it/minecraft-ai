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
    private static final Path DIR  = FMLPaths.GAMEDIR.get().resolve("agent");
    private static final Path FILE = DIR.resolve("events.json");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static long lastWriteTick = -1;

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        MinecraftServer server = e.getServer();
        ServerLevel level = server.overworld();
        long tick = level.getGameTime();
        if (tick == lastWriteTick) return;
        lastWriteTick = tick;

        try {
            Files.createDirectories(DIR);

            // 单人世界时，取第一个玩家；多人你可以循环写多条
            ServerPlayer player = server.getPlayerList().getPlayers().isEmpty()
                    ? null
                    : server.getPlayerList().getPlayers().get(0);

            JsonObject j = new JsonObject();
            j.addProperty("tick", tick);
            j.addProperty("isDay", level.isDay());

            BlockPos bp = (player != null) ? player.blockPosition() : new BlockPos(0, 64, 0);
            j.addProperty("x", bp.getX());
            j.addProperty("y", bp.getY());
            j.addProperty("z", bp.getZ());
            // === 更精确的光照信息（在玩家头部位置采样） ===
            BlockPos samplePos = bp.above(); // 头部附近更贴近“眼睛看见的亮度”
            int sky  = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, samplePos);
            int block= level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, samplePos);

            j.addProperty("skyLight",  sky);                    // 天空光 0..15
            j.addProperty("blockLight", block);                 // 方块光 0..15（火把等）
            j.addProperty("canSeeSky", level.canSeeSky(samplePos));
            j.addProperty("light", Math.max(sky, block));       // 兼容旧逻辑：取两者最大
            // === 玩家生命/饥饿状态（AI 需要用） ===
            if (player != null) {
                j.addProperty("health", player.getHealth());           // 当前生命（float 0..20）
                j.addProperty("maxHealth", player.getMaxHealth());     // 最大生命（float）
                j.addProperty("food", player.getFoodData().getFoodLevel());         // 饥饿值 0..20
                j.addProperty("saturation", player.getFoodData().getSaturationLevel()); // 饱和度
            }




            int hostiles = level.getEntitiesOfClass(Monster.class, new AABB(bp).inflate(16)).size();
            j.addProperty("hostiles", hostiles);

            try (OutputStream out = Files.newOutputStream(
                    FILE, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                out.write((GSON.toJson(j) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }
}
