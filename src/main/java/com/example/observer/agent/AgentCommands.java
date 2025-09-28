package com.example.observer.agent;

import com.example.observer.ObserverMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * /agent 系列命令：
 *   /agent place <block> <pos>           在指定位置放置方块
 *   /agent break <pos>                   破坏指定方块（掉落物品）
 *   /agent kill <radius>                 清理半径内所有非玩家的 LivingEntity
 *   /agent rule doDaylightCycle <bool>   设置昼夜更替
 *   /agent heal                          执行者满血满饱食、清除负面状态
 *   /agent air                           立刻回满氧气（溺水时救急）
 */
@Mod.EventBusSubscriber(modid = ObserverMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AgentCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("agent").requires(src -> src.hasPermission(2))

                // /agent place <block> <pos>
                .then(Commands.literal("place")
                        .then(Commands.argument("block", BlockStateArgument.block(event.getBuildContext()))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> {
                                            ServerLevel level = ctx.getSource().getLevel();
                                            BlockState state = BlockStateArgument.getBlock(ctx, "block").getState();
                                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                            level.setBlock(pos, state, 3);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Placed " + state.getBlock() + " @ " + pos.toShortString()),
                                                    true);
                                            return 1;
                                        }))))


                // /agent break <pos>
                .then(Commands.literal("break")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> {
                                    ServerLevel level = ctx.getSource().getLevel();
                                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                    boolean ok = level.destroyBlock(pos, true);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal((ok ? "Broke " : "Nothing at ") + pos.toShortString()),
                                            true);
                                    return ok ? 1 : 0;
                                })))


                // /agent kill <radius:int 1..128>
                .then(Commands.literal("kill")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                .executes(ctx -> {
                                    ServerLevel level = ctx.getSource().getLevel();
                                    int r = IntegerArgumentType.getInteger(ctx, "radius");

                                    // 以执行者为中心构造范围盒；执行者可能不是 LivingEntity，所以先按 Entity 处理
                                    AABB box;
                                    Entity src = ctx.getSource().getEntity(); // 可能为 null（命令方块/服务器控制台）
                                    if (src != null) {
                                        box = src.getBoundingBox().inflate(r);
                                    } else {
                                        BlockPos p = BlockPos.containing(ctx.getSource().getPosition());
                                        box = new AABB(p).inflate(r);
                                    }

                                    AtomicInteger n = new AtomicInteger(0);
                                    for (LivingEntity ent : level.getEntitiesOfClass(
                                            LivingEntity.class, box, e2 -> !(e2 instanceof ServerPlayer))) {
                                        ent.remove(Entity.RemovalReason.KILLED);
                                        n.incrementAndGet();
                                    }

                                    int count = n.get();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Removed " + count + " entities"), true);
                                    return count;
                                })))



                // /agent rule doDaylightCycle <value:bool>
                .then(Commands.literal("rule")
                        .then(Commands.literal("doDaylightCycle")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            ServerLevel level = ctx.getSource().getLevel();
                                            boolean v = BoolArgumentType.getBool(ctx, "value");
                                            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(v, level.getServer());
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("doDaylightCycle=" + v),
                                                    true);
                                            return 1;
                                        }))))



                // /agent heal  —— 满血、满饱食、清负面（不依赖药水类）
                .then(Commands.literal("heal")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.setHealth(p.getMaxHealth());
                            p.getFoodData().setFoodLevel(20);
                            p.getFoodData().setSaturation(20);
                            p.removeAllEffects();
                            ctx.getSource().sendSuccess(() -> Component.literal("Healed."), true);
                            return 1;
                        }))

                // /agent air —— 立刻回满氧气
                .then(Commands.literal("air")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.setAirSupply(p.getMaxAirSupply());
                            ctx.getSource().sendSuccess(() -> Component.literal("Air restored."), true);
                            return 1;
                        }))

        );
    }
}
