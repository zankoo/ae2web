package com.kokoda.ae2web.web.handlers.crafting;

import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.util.IconKeyUtils;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

public final class CpuHandler {
    private CpuHandler() {}

    public static void handleCpus(ChannelHandlerContext ctx, FullHttpRequest request,
                                  Map<String, List<String>> params, HttpHeaders headers) {
        runOnServerThread(ctx, headers, () -> {
            String terminalId = getFirstParam(params, "terminalId");
            WebTerminalBlockEntity terminal = getConnectedTerminal(terminalId);
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

            List<Map<String, Object>> cpus = new ArrayList<>();
            for (ICraftingCPU cpu : craftingService.getCpus()) {
                cpus.add(cpuToJson(cpu));
            }

            Map<String, Object> response = Map.of(
                    "cpus", cpus,
                    "total", cpus.size()
            );
            sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(response), headers);
        });
    }

    private static Map<String, Object> cpuToJson(ICraftingCPU cpu) {
        Map<String, Object> info = new HashMap<>();
        info.put("busy", cpu.isBusy());
        info.put("availableStorage", cpu.getAvailableStorage());
        info.put("coProcessors", cpu.getCoProcessors());
        info.put("selectionMode", cpu.getSelectionMode().toString());
        info.put("name", cpu.getName() != null ? cpu.getName().getString() : null);

        CraftingJobStatus status = cpu.getJobStatus();
        if (status != null) {
            Map<String, Object> jobStatus = new HashMap<>();
            if (status.crafting() != null) {
                PrerenderStorageService storage = PrerenderStorageService.getInstance();
                jobStatus.put("crafting", genericStackToJson(status.crafting(), storage));
            }
            OutputProgress outputProgress = getOutputProgress(cpu, status);
            if (outputProgress != null) {
                jobStatus.put("outputTotal", outputProgress.total());
                jobStatus.put("outputDone", outputProgress.done());
                jobStatus.put("outputRemaining", outputProgress.remaining());
            }
            jobStatus.put("totalItems", status.totalItems());
            jobStatus.put("progress", status.progress());
            jobStatus.put("elapsedTimeNanos", status.elapsedTimeNanos());
            info.put("jobStatus", jobStatus);
        }

        return info;
    }

    private static Map<String, Object> genericStackToJson(GenericStack stack, PrerenderStorageService storage) {
        AEKey key = stack.what();
        String name;
        String translationKey = null;
        if (key instanceof AEItemKey itemKey) {
            translationKey = itemKey.getItem().getDescriptionId();
            String translation = storage.getTranslation(IconKeyUtils.iconKey(itemKey));
            name = translation != null ? translation : itemKey.getDisplayName().getString();
        } else if (key instanceof AEFluidKey fluidKey) {
            translationKey = fluidKey.getFluid().getFluidType().getDescriptionId();
            String translation = storage.getTranslation(IconKeyUtils.iconKey(fluidKey));
            name = translation != null ? translation : fluidKey.getDisplayName().getString();
        } else {
            name = key.getDisplayName().getString();
        }
        return Map.of(
                "id", key.getId().toString(),
                "iconKey", IconKeyUtils.iconKey(key),
                "name", name,
                "translationKey", translationKey != null ? translationKey : "",
                "amount", stack.amount()
        );
    }

    private static OutputProgress getOutputProgress(ICraftingCPU cpu, CraftingJobStatus status) {
        Long remaining = readFinalOutputRemainingFromCpu(cpu);
        if (remaining == null) {
            return null;
        }

        Long total = readFinalOutputTotalFromCpu(cpu);
        if (total == null && status.crafting() != null) {
            total = status.crafting().amount();
        }
        if (total == null || total <= 0) {
            return null;
        }

        long clampedRemaining = Math.max(0L, Math.min(remaining, total));
        long done = total - clampedRemaining;
        return new OutputProgress(total, done, clampedRemaining);
    }

    private static Long readFinalOutputRemainingFromCpu(ICraftingCPU cpu) {
        Object job = readCpuExecutingJob(cpu);
        if (job == null) {
            return null;
        }

        try {
            var remainingField = job.getClass().getDeclaredField("remainingAmount");
            remainingField.setAccessible(true);
            Object remaining = remainingField.get(job);
            if (remaining instanceof Number number) {
                return number.longValue();
            }
            return null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Long readFinalOutputTotalFromCpu(ICraftingCPU cpu) {
        Object job = readCpuExecutingJob(cpu);
        if (job == null) {
            return null;
        }

        try {
            var finalOutputField = job.getClass().getDeclaredField("finalOutput");
            finalOutputField.setAccessible(true);
            Object finalOutput = finalOutputField.get(job);
            if (finalOutput == null) {
                return null;
            }

            var amountMethod = finalOutput.getClass().getMethod("amount");
            Object amount = amountMethod.invoke(finalOutput);
            if (amount instanceof Number number) {
                return number.longValue();
            }
            return null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object readCpuExecutingJob(ICraftingCPU cpu) {
        if (cpu == null) {
            return null;
        }

        try {
            var craftingLogicField = cpu.getClass().getField("craftingLogic");
            Object craftingLogic = craftingLogicField.get(cpu);
            if (craftingLogic == null) {
                return null;
            }

            var jobField = craftingLogic.getClass().getDeclaredField("job");
            jobField.setAccessible(true);
            return jobField.get(craftingLogic);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private record OutputProgress(long total, long done, long remaining) {
    }

    public static List<Map<String, Object>> keyCounterToStackList(appeng.api.stacks.KeyCounter keyCounter) {
        List<Map<String, Object>> missing = new ArrayList<>();
        if (keyCounter == null) {
            return missing;
        }

        PrerenderStorageService storage = PrerenderStorageService.getInstance();
        for (Object2LongMap.Entry<AEKey> entry : keyCounter) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (key == null || amount <= 0) {
                continue;
            }
            missing.add(genericStackToJson(new GenericStack(key, amount), storage));
        }
        return missing;
    }
}
