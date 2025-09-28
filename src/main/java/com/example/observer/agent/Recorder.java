package com.example.observer.agent;

import com.example.observer.ObserverMod;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = ObserverMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Recorder {
    private static final Path DIR  = FMLPaths.GAMEDIR.get().resolve("observer-logs");
    private static final Path FILE = DIR.resolve("events.jsonl");

    private static void log(String json) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(FILE, json + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        var pos = e.getPos();
        var key = ForgeRegistries.BLOCKS.getKey(e.getState().getBlock());
        String by = (e.getPlayer() == null) ? "unknown" : e.getPlayer().getGameProfile().getName();
        log(String.format(Locale.ROOT,
                "{\"ts\":%d,\"type\":\"break\",\"x\":%d,\"y\":%d,\"z\":%d,\"block\":\"%s\",\"by\":\"%s\"}",
                e.getPlayer().level().getGameTime(), pos.getX(), pos.getY(), pos.getZ(), key, by));
        if (AgentFlags.cancelBlockBreaks) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        var pos = e.getPos();
        var key = ForgeRegistries.BLOCKS.getKey(e.getPlacedBlock().getBlock());
        String by = (e.getEntity() == null) ? "unknown" : e.getEntity().getName().getString();
        log(String.format(Locale.ROOT,
                "{\"ts\":%d,\"type\":\"place\",\"x\":%d,\"y\":%d,\"z\":%d,\"block\":\"%s\",\"by\":\"%s\"}",
                e.getEntity().level().getGameTime(), pos.getX(), pos.getY(), pos.getZ(), key, by));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        log(String.format(Locale.ROOT,
                "{\"ts\":%d,\"type\":\"death\",\"entity\":\"%s\"}",
                e.getEntity().level().getGameTime(), e.getEntity().getName().getString()));
    }
}
