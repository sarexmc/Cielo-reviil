package com.dedsafio;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod(DedsafioMod.MODID)
@Mod.EventBusSubscriber(modid = DedsafioMod.MODID)
public class DedsafioMod {
    public static final String MODID = "dedsafio";
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public DedsafioMod() {
        CHANNEL.registerMessage(0, DedPacket.class, DedPacket::encode, DedPacket::decode, DedPacket::handle);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("dedsafio").requires(s -> s.hasPermission(2))
                .then(Commands.literal("sky")
                        .executes(c -> simple(c.getSource(), DedPacket.SKY_ON, "Cielo rojo activado"))
                        .then(Commands.literal("on").executes(c -> simple(c.getSource(), DedPacket.SKY_ON, "Cielo rojo activado")))
                        .then(Commands.literal("off").executes(c -> simple(c.getSource(), DedPacket.SKY_OFF, "Cielo normal"))))
                .then(Commands.literal("start")
                        .then(Commands.literal("vertical").executes(c -> start(c.getSource(), DedPacket.START_V)))
                        .then(Commands.literal("horizontal").executes(c -> start(c.getSource(), DedPacket.START_H))))
                .then(Commands.literal("stop").executes(c -> simple(c.getSource(), DedPacket.STOP, "Grieta cerrada"))));
    }

    private static int simple(CommandSourceStack src, int action, String msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new DedPacket(action, 0, 0, 0, 0, 1));
        src.sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int start(CommandSourceStack src, int action) {
        Vec3 pos = src.getPosition();
        Vec3 f = Vec3.directionFromRotation(0, src.getRotation().y);
        CHANNEL.send(PacketDistributor.ALL.noArg(),
                new DedPacket(action, pos.x + f.x * 90, pos.y + 60, pos.z + f.z * 90, (float) f.x, (float) f.z));
        src.sendSuccess(() -> Component.literal("Grieta iniciada"), true);
        return 1;
    }
}
