package com.kokoda.ae2web.web.handlers.crafting;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

public final class CraftRequestHandler {
    private static final int CRAFT_CALCULATION_TIMEOUT_SECONDS = 10;

    private CraftRequestHandler() {}

    public static void handleCraft(ChannelHandlerContext ctx, FullHttpRequest request,
                                   Map<String, java.util.List<String>> params, HttpHeaders headers) {
        String body = request.content().toString(CharsetUtil.UTF_8);
        CraftRequestParser.CraftRequest craftRequest;
        try {
            craftRequest = CraftRequestParser.parse(body);
        } catch (IllegalArgumentException e) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Invalid craft request"), headers);
            return;
        }

        runOnServerThread(ctx, headers, () -> executeCraftRequest(ctx, headers, craftRequest));
    }

    private record CraftContext(
            ChannelHandlerContext ctx,
            HttpHeaders headers,
            ICraftingService craftingService,
            appeng.api.networking.security.IActionSource actionSource,
            String cpuName
    ) {}

    private static void executeCraftRequest(ChannelHandlerContext ctx, HttpHeaders headers, CraftRequestParser.CraftRequest request) {
        WebTerminalBlockEntity terminal = getConnectedTerminal(request.terminalId());
        if (terminal == null) {
            sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    errorJson("No connected terminal available"), headers);
            return;
        }

        ICraftingService craftingService = terminal.getCraftingService();
        if (craftingService == null) {
            sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    errorJson("Crafting service not available"), headers);
            return;
        }

        var actionSource = terminal.getActionSource();
        if (actionSource == null) {
            sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    errorJson("Action source not available"), headers);
            return;
        }

        ResourceLocation keyId = ResourceLocation.tryParse(request.id());
        if (keyId == null) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Invalid id"), headers);
            return;
        }

        AEKey key;
        if (request.keyType() == CraftRequestParser.CraftKeyType.FLUID) {
            var fluid = BuiltInRegistries.FLUID.getOptional(keyId).orElse(null);
            if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                        errorJson("Unknown fluidId"), headers);
                return;
            }
            key = AEFluidKey.of(fluid);
        } else {
            var item = BuiltInRegistries.ITEM.getOptional(keyId).orElse(null);
            if (item == null) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                        errorJson("Unknown itemId"), headers);
                return;
            }
            key = AEItemKey.of(item);
        }

        if (!craftingService.isCraftable(key)) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Target is not craftable"), headers);
            return;
        }

        var level = terminal.getTerminalLevel();
        if (level == null) {
            sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    errorJson("Terminal level not available"), headers);
            return;
        }

        Future<ICraftingPlan> planFuture = craftingService.beginCraftingCalculation(
                level,
                () -> actionSource,
                key,
                request.amount(),
                CalculationStrategy.REPORT_MISSING_ITEMS
        );

        CraftContext craftContext = new CraftContext(ctx, headers, craftingService, actionSource, request.cpuName());

        CompletableFuture
                .supplyAsync(() -> getCraftingPlan(planFuture), CraftingExecutors.get())
                .thenAcceptAsync(plan -> processCraftingPlan(craftContext, plan), r -> scheduleOnServerThread(r));
    }

    private record CraftingPlanResult(ICraftingPlan plan, String error) {}

    private static CraftingPlanResult getCraftingPlan(Future<ICraftingPlan> planFuture) {
        try {
            ICraftingPlan plan = planFuture.get(CRAFT_CALCULATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return new CraftingPlanResult(plan, null);
        } catch (TimeoutException e) {
            return new CraftingPlanResult(null, "timeout");
        } catch (Exception e) {
            return new CraftingPlanResult(null, "error");
        }
    }

    private static void processCraftingPlan(CraftContext ctx, CraftingPlanResult result) {
        if (!ctx.ctx().channel().isActive()) {
            return;
        }

        if (result.error() != null) {
            if ("timeout".equals(result.error())) {
                sendJsonResponse(ctx.ctx(), HttpResponseStatus.GATEWAY_TIMEOUT,
                        errorJson("Crafting calculation timed out"), ctx.headers());
            } else {
                sendJsonResponse(ctx.ctx(), HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        errorJson("Crafting calculation failed"), ctx.headers());
            }
            return;
        }

        ICraftingPlan plan = result.plan();
        if (plan == null) {
            sendJsonResponse(ctx.ctx(), HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    errorJson("Internal Server Error"), ctx.headers());
            return;
        }

        if (plan.simulation()) {
            Map<String, Object> response = Map.of(
                    "error", "Missing items for crafting request",
                    "missing", CpuHandler.keyCounterToStackList(plan.missingItems())
            );
            sendJsonResponse(ctx.ctx(), HttpResponseStatus.BAD_REQUEST, GSON.toJson(response), ctx.headers());
            return;
        }

        ICraftingCPU targetCpu = null;
        if (ctx.cpuName() != null && !ctx.cpuName().isEmpty()) {
            for (ICraftingCPU cpu : ctx.craftingService().getCpus()) {
                if (cpu.getName() != null && ctx.cpuName().equals(cpu.getName().getString())) {
                    targetCpu = cpu;
                    break;
                }
            }
        }

        var submitResult = ctx.craftingService().submitJob(plan, null, targetCpu, false, ctx.actionSource());
        if (!submitResult.successful()) {
            sendJsonResponse(ctx.ctx(), HttpResponseStatus.CONFLICT,
                    errorJson(mapCraftingSubmitError(submitResult.errorCode())), ctx.headers());
            return;
        }

        sendJsonResponse(ctx.ctx(), HttpResponseStatus.OK, GSON.toJson(Map.of("status", "submitted")), ctx.headers());
    }

    private static String mapCraftingSubmitError(CraftingSubmitErrorCode errorCode) {
        if (errorCode == null) {
            return "Unable to submit crafting job";
        }

        return switch (errorCode) {
            case INCOMPLETE_PLAN -> "Crafting plan incomplete";
            case NO_CPU_FOUND, NO_SUITABLE_CPU_FOUND -> "No crafting CPU available";
            case CPU_BUSY -> "Crafting CPU is busy";
            case CPU_OFFLINE -> "Crafting CPU is offline";
            case CPU_TOO_SMALL -> "Crafting CPU is too small";
            case MISSING_INGREDIENT -> "Missing ingredient";
        };
    }
}
