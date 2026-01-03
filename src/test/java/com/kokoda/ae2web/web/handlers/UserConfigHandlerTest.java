package com.kokoda.ae2web.web.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UserConfigHandlerTest {
    @Test
    void postInventoryConfigDoesNotCrashWhenRequestIsReleasedBeforeServerThreadRuns() throws Exception {
        List<Runnable> scheduled = new ArrayList<>();
        MinecraftServer server = Mockito.mock(MinecraftServer.class);
        Mockito.doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            scheduled.add(task);
            return null;
        }).when(server).execute(Mockito.any(Runnable.class));

        UUID userUuid = UUID.randomUUID();

        try (var ignored = installCurrentServer(server)) {
            EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
            ChannelHandlerContext ctx = channel.pipeline().firstContext();
            ctx.channel().attr(HandlerUtils.AUTHENTICATED_USER_UUID).set(userUuid);

            String body = """
                    {"whitelist":[],"blacklist":[],"categories":[{"name":"粗矿&原矿","tagRegex":"forge:raw_materials"}],"collapsed":{}}
                    """;
            FullHttpRequest request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.POST,
                    "/api/user/inventoryConfig?terminalId=93444682",
                    io.netty.buffer.Unpooled.copiedBuffer(body, CharsetUtil.UTF_8)
            );

            UserConfigHandler.handleInventoryConfig(
                    ctx,
                    request,
                    Map.of("terminalId", List.of("93444682")),
                    new DefaultHttpHeaders()
            );

            // Simulate Netty auto-release once the handler returns.
            ReferenceCountUtil.release(request);

            assertThat(scheduled).hasSize(1);
            scheduled.get(0).run();

            FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
            try {
                assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
            } finally {
                ReferenceCountUtil.release(resp);
            }
        }
    }

    private static AutoCloseable installCurrentServer(MinecraftServer server) throws Exception {
        Field direct = null;
        for (Field f : ServerLifecycleHooks.class.getDeclaredFields()) {
            if (MinecraftServer.class.isAssignableFrom(f.getType())) {
                direct = f;
                break;
            }
        }
        if (direct != null) {
            Field target = direct;
            target.setAccessible(true);
            Object old = target.get(null);
            target.set(null, server);
            return () -> target.set(null, old);
        }

        Field atomicRefField = null;
        for (Field f : ServerLifecycleHooks.class.getDeclaredFields()) {
            if (AtomicReference.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                Object value = f.get(null);
                if (value instanceof AtomicReference<?> ref && (ref.get() == null || ref.get() instanceof MinecraftServer)) {
                    atomicRefField = f;
                    break;
                }
            }
        }
        if (atomicRefField != null) {
            Field target = atomicRefField;
            target.setAccessible(true);
            @SuppressWarnings("unchecked")
            AtomicReference<MinecraftServer> ref = (AtomicReference<MinecraftServer>) target.get(null);
            MinecraftServer old = ref.get();
            ref.set(server);
            return () -> ref.set(old);
        }

        throw new IllegalStateException("Unable to set current server for test (ServerLifecycleHooks internals changed).");
    }
}
