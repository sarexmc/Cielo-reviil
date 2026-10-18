package com.dedsafio;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DedPacket {
    public static final int SKY_ON = 0, SKY_OFF = 1, START_V = 2, START_H = 3, STOP = 4;
    public final int action;
    public final double x, y, z;
    public final float fx, fz;

    public DedPacket(int action, double x, double y, double z, float fx, float fz) {
        this.action = action; this.x = x; this.y = y; this.z = z; this.fx = fx; this.fz = fz;
    }

    public static void encode(DedPacket p, FriendlyByteBuf b) {
        b.writeInt(p.action);
        b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
        b.writeFloat(p.fx); b.writeFloat(p.fz);
    }

    public static DedPacket decode(FriendlyByteBuf b) {
        return new DedPacket(b.readInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat());
    }

    public static void handle(DedPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientEvents.apply(p)));
        ctx.get().setPacketHandled(true);
    }
}
