package com.example.observer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

public final class EnergyAdapter {
    public record Snapshot(String bestLabel, int stored, int max, double dist) {}

    public static Optional<Snapshot> getEnergy(BlockEntity be) {
        if (be == null || be.getLevel() == null) return Optional.empty();

        IEnergyStorage es = be.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        if (es == null) return Optional.empty();

        int cur = es.getEnergyStored();
        int max = es.getMaxEnergyStored();
        if (max <= 0 && cur <= 0) return Optional.empty();

        var state = be.getBlockState();
        String label = state.getBlock().getName().getString();
        if (label == null || label.isBlank()) {
            var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            label = key == null ? "Unknown" : key.toString();
        }

        double dist = 0.0;
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            var p = be.getBlockPos();
            dist = Math.sqrt(p.distToCenterSqr(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        }

        return Optional.of(new Snapshot(label, cur, max, dist));
    }

    private EnergyAdapter() {}
}
