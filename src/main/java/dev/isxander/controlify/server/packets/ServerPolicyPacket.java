package dev.isxander.controlify.server.packets;

import dev.isxander.controlify.platform.network.ControlifyPacketCodec;
import dev.isxander.controlify.utils.CUtil;
import net.minecraft.resources.ResourceLocation;

public record ServerPolicyPacket(String id, boolean allowed) {
    public static final ResourceLocation CHANNEL = CUtil.rl("server_policy");

    public static final ControlifyPacketCodec<ServerPolicyPacket> CODEC = ControlifyPacketCodec.of(
        (buf, packet) -> {
            buf.writeUtf(packet.id());
            buf.writeBoolean(packet.allowed());
        },
        buf -> new ServerPolicyPacket(
            buf.readUtf(),
            buf.readBoolean()
        )
    );
}
