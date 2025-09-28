package com.example.observer.agent;

import com.example.observer.ObserverMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = ObserverMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandFileRunner {
    private static final Path DIR  = FMLPaths.GAMEDIR.get().resolve("agent");
    // ⚠️ 这里的名字要和 Python 脚本一致（你用 jsonl 就写 jsonl；用 json 就写 json）
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
                // 处理可能的编码问题
                String s = new String(line.getBytes("ISO-8859-1"), StandardCharsets.UTF_8);
                handleLine(e.getServer(), s);
            }
            lastSize = raf.getFilePointer();
        } catch (Exception ignored) {}
    }

    private static void handleLine(MinecraftServer server, String line) {
        try {
            JsonObject j = JsonParser.parseString(line).getAsJsonObject();
            String op = j.has("op") ? j.get("op").getAsString() : "";

            // 单机/局域网一般只有你一个玩家；这里取“在线的第一个玩家”为目标
            ServerPlayer player = server.getPlayerList().getPlayers().isEmpty()
                    ? null
                    : server.getPlayerList().getPlayers().get(0);

            ServerLevel level = server.overworld();

            switch (op) {
                case "place" -> {
                    String id = j.get("block").getAsString();
                    BlockState state = BuiltInRegistries.BLOCK.get(new ResourceLocation(id)).defaultBlockState();
                    int x = j.get("x").getAsInt(), y = j.get("y").getAsInt(), z = j.get("z").getAsInt();
                    level.setBlock(new BlockPos(x, y, z), state, 3);
                }
                case "break" -> {
                    int x = j.get("x").getAsInt(), y = j.get("y").getAsInt(), z = j.get("z").getAsInt();
                    level.destroyBlock(new BlockPos(x, y, z), true);
                }
                case "kill" -> {
                    int r = j.get("radius").getAsInt();
                    BlockPos center = player != null ? player.blockPosition() : level.getSharedSpawnPos();
                    AABB box = new AABB(center).inflate(r);
                    for (LivingEntity ent : level.getEntitiesOfClass(LivingEntity.class, box,
                            e -> !(e instanceof ServerPlayer))) {
                        ent.remove(Entity.RemovalReason.KILLED);
                    }
                }
                case "gamerule" -> {
                    boolean v = j.get("value").getAsBoolean();
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(v, server);
                }
                case "heal" -> {
                    if (player != null) {
                        float max = player.getMaxHealth();
                        player.setHealth(max);
                        // 加一点短暂再生，处理持续伤害场景
                        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, false));
                    }
                }
                case "feed" -> {
                    if (player != null) {
                        FoodData f = player.getFoodData();
                        f.setFoodLevel(20);
                        f.setSaturation(20f);
                    }
                }
                case "air" -> {
                    if (player != null) {
                        // 立即回满氧气
                        player.setAirSupply(player.getMaxAirSupply());
                        // 再给短暂水下呼吸，避免下一拍又掉氧
                        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 15, 0, false, false));
                        // 如果正在窒息（卡方块里），也给个缓降/缓慢掉落避免伤害（可选）
                        player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 20 * 10, 0, false, false));
                    }
                }
                case "give" -> {
                    if (player != null) {
                        String itemId = j.get("item").getAsString();
                        int count = j.get("count").getAsInt();
                        var item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
                        var stack = new net.minecraft.world.item.ItemStack(item, Math.min(64, Math.max(1, count)));
                        player.addItem(stack);
                    }
                }
                case "effect" -> {
                    if (player != null) {
                        String effId = j.get("id").getAsString();
                        int seconds = j.get("seconds").getAsInt();
                        int amp = j.has("amplifier") ? j.get("amplifier").getAsInt() : 0;
                        var eff = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(effId));
                        if (eff != null) {
                            player.addEffect(new MobEffectInstance(eff, seconds * 20, amp, false, false));
                        }
                    }
                }
                default -> {}
            }
        } catch (Exception ignored) {}
    }
}
