package com.example.observer.agent;

import com.example.observer.ObserverMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicInteger;

@Mod.EventBusSubscriber(modid = ObserverMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AgentCommands {
    private AgentCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(
                Commands.literal("agent")
                        .requires(src -> src.hasPermission(2))

                        // --- /agent place <block> <x y z>
                        .then(Commands.literal("place")
                                .then(Commands.argument("block", BlockStateArgument.block(event.getBuildContext()))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> {
                                                    ServerLevel level = ctx.getSource().getLevel();
                                                    BlockState state = BlockStateArgument.getBlock(ctx, "block").getState();
                                                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                                    level.setBlock(pos, state, 3);
                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("Placed " + state.getBlock() + " @ " + pos.toShortString()), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        // --- /agent break <x y z>
                        .then(Commands.literal("break")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> {
                                            ServerLevel level = ctx.getSource().getLevel();
                                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                            boolean ok = level.destroyBlock(pos, true);
                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal((ok ? "Broke " : "Nothing at ") + pos.toShortString()), true);
                                            return ok ? 1 : 0;
                                        })
                                )
                        )

                        // --- /agent kill <radius>
                        .then(Commands.literal("kill")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                        .executes(ctx -> {
                                            ServerLevel level = ctx.getSource().getLevel();
                                            int r = IntegerArgumentType.getInteger(ctx, "radius");

                                            AABB box;
                                            try {
                                                box = ctx.getSource().getEntityOrException().getBoundingBox().inflate(r);
                                            } catch (CommandSyntaxException ex) {
                                                box = new AABB(BlockPos.containing(ctx.getSource().getPosition())).inflate(r);
                                            }

                                            AtomicInteger n = new AtomicInteger(0);
                                            for (LivingEntity ent : level.getEntitiesOfClass(
                                                    LivingEntity.class, box, e2 -> !(e2 instanceof ServerPlayer))) {
                                                ent.remove(Entity.RemovalReason.KILLED);
                                                n.incrementAndGet();
                                            }

                                            int removed = n.get();
                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Removed " + removed + " entities"), true);
                                            return removed;
                                        })
                                )
                        )

                        // --- /agent rule doDaylightCycle <true|false>
                        .then(Commands.literal("rule")
                                .then(Commands.literal("doDaylightCycle")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    ServerLevel level = ctx.getSource().getLevel();
                                                    boolean v = BoolArgumentType.getBool(ctx, "value");
                                                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(v, level.getServer());
                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("doDaylightCycle=" + v), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        // --- /agent heal  （满血+短暂再生）
                        .then(Commands.literal("heal")
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    p.setHealth(p.getMaxHealth());
                                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, false));
                                    ctx.getSource().sendSuccess(() -> Component.literal("healed"), true);
                                    return 1;
                                })
                        )

                        // --- /agent feed  （满饱食度）
                        .then(Commands.literal("feed")
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    p.getFoodData().setFoodLevel(20);
                                    p.getFoodData().setSaturation(20f);
                                    ctx.getSource().sendSuccess(() -> Component.literal("fed"), true);
                                    return 1;
                                })
                        )

                        // --- /agent air  （回满氧气+短暂水下呼吸）
                        .then(Commands.literal("air")
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    p.setAirSupply(p.getMaxAirSupply());
                                    p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 15, 0, false, false));
                                    ctx.getSource().sendSuccess(() -> Component.literal("air restored"), true);
                                    return 1;
                                })
                        )
        );
    }
}
