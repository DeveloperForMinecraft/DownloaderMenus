package com.MenuDownloader;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;

public final class PacketSniffer {

    public interface Hook {
        void onPacket(boolean outbound, IPacket<?> packet);
    }

    private static final String HANDLER_NAME = "menudownloader_logic";

    static volatile Hook hook;

    public static void inject() {
        if (hook == null) {
            return;
        }
        try {
            NetworkManager manager = Minecraft.getInstance().getConnection() != null
                    ? Minecraft.getInstance().getConnection().getConnection()
                    : null;
            Channel channel = manager == null ? null : manager.channel();
            if (channel == null || !channel.isActive() || channel.pipeline().get(HANDLER_NAME) != null) {
                return;
            }
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    try {
                        if (msg instanceof IPacket) {
                            hook.onPacket(false, (IPacket<?>) msg);
                        }
                    } catch (Throwable ignored) {
                    }
                    super.channelRead(ctx, msg);
                }

                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    try {
                        if (msg instanceof IPacket) {
                            hook.onPacket(true, (IPacket<?>) msg);
                        }
                    } catch (Throwable ignored) {
                    }
                    super.write(ctx, msg, promise);
                }
            });
        } catch (Throwable t) {
            System.out.println("[MenuDownloader] Не удалось встроить перехват пакетов: " + t);
        }
    }
}
