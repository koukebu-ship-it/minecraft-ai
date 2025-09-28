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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicInteger;

@Mod.EventBusSubscriber(modid = ObserverMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AgentCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("agent").requires(src -> src.hasPermission(2))
                .then(Commands.literal("place")
                        .then(Commands.argument("block", BlockStateArgument.block(event.getBuildContext()))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> {
                                            ServerLevel level = ctx.getSource().getLevel();
                                            BlockState state = BlockStateArgument.getBlock(ctx,"block").getState();
                                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx,"pos");
                                            level.setBlock(pos, state, 3);
                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Placed " + state.getBlock() + " @ " + pos.toShortString()), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("break")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> {
                                    ServerLevel level = ctx.getSource().getLevel();
                                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx,"pos");
                                    boolean ok = level.destroyBlock(pos, true);
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal((ok ? "Broke " : "Nothing at ") + pos.toShortString()), true);
                                    return ok ? 1 : 0;
                                })))
                .then(Commands.literal("kill")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1,128))
                                .executes(ctx -> {
                                    ServerLevel level = ctx.getSource().getLevel();
                                    int r = IntegerArgumentType.getInteger(ctx,"radius");

                                    AABB box;
                                    try {
                                        box = ctx.getSource().getEntityOrException().getBoundingBox().inflate(r);
                                    } catch (CommandSyntaxException ex) {
                                        // 修正: 将 Vec3 转换为 BlockPos
                                        box = new AABB(BlockPos.containing(ctx.getSource().getPosition())).inflate(r);
                                    }

                                    AtomicInteger n = new AtomicInteger(0);
                                    for (LivingEntity ent : level.getEntitiesOfClass(LivingEntity.class, box,
                                            e2 -> !(e2 instanceof ServerPlayer))) {
                                        ent.remove(Entity.RemovalReason.KILLED);
                                        n.incrementAndGet();
                                    }

                                    final int finalN = n.get();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Removed " + finalN + " entities"), true);
                                    return finalN;
                                })))
                .then(Commands.literal("rule")
                        .then(Commands.literal("doDaylightCycle")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            ServerLevel level = ctx.getSource().getLevel();
                                            boolean v = BoolArgumentType.getBool(ctx,"value");
                                            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(v, level.getServer());
                                            ctx.getSource().sendSuccess(() -> Component.literal("doDaylightCycle=" + v), true);
                                            return 1;
                                        }))))
        );
    }
}