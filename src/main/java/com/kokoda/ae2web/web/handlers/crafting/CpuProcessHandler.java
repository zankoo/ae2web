package com.kokoda.ae2web.web.handlers.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.util.IconKeyUtils;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

public final class CpuProcessHandler {
    private CpuProcessHandler() {}

    public static void handleCpuProcess(ChannelHandlerContext ctx, FullHttpRequest request,
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

            String cpuId = getFirstParam(params, "cpuId");
            if (cpuId == null || cpuId.isBlank()) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                        errorJson("Missing cpuId"), headers);
                return;
            }

            ICraftingCPU cpu = findCpuById(craftingService, cpuId);
            if (cpu == null) {
                sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                        errorJson("CPU not found: " + cpuId), headers);
                return;
            }

            if (!cpu.isBusy()) {
                sendJsonResponse(ctx, HttpResponseStatus.CONFLICT,
                        errorJson("CPU is not busy"), headers);
                return;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("cpuId", CpuHandler.cpuIdOf(cpu));
            response.put("busy", true);
            response.put("name", cpu.getName() != null ? cpu.getName().getString() : null);

            CraftingJobStatus status = cpu.getJobStatus();
            Object craftingLogic = readCpuCraftingLogic(cpu);
            Object executingJob = CpuHandler.readCpuExecutingJob(cpu);

            response.put("summary", buildSummary(status, executingJob));
            response.put("entries", buildEntries(craftingLogic));
            response.put("patterns", buildPatterns(executingJob));

            sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(response), headers);
        });
    }

    private static ICraftingCPU findCpuById(ICraftingService craftingService, String cpuId) {
        for (ICraftingCPU cpu : craftingService.getCpus()) {
            if (cpuId.equals(CpuHandler.cpuIdOf(cpu))) {
                return cpu;
            }
        }
        return null;
    }

    private static Object readCpuCraftingLogic(ICraftingCPU cpu) {
        if (cpu == null) {
            return null;
        }
        try {
            Field logicField = cpu.getClass().getField("craftingLogic");
            return logicField.get(cpu);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Map<String, Object> buildSummary(CraftingJobStatus status, Object executingJob) {
        Map<String, Object> summary = new HashMap<>();
        if (status != null) {
            if (status.crafting() != null) {
                summary.put("finalOutput", CpuHandler.genericStackToJson(status.crafting()));
            }
            summary.put("totalItems", status.totalItems());
            summary.put("progress", status.progress());
            summary.put("elapsedTimeNanos", status.elapsedTimeNanos());
        }

        Long remaining = null;
        Long total = null;
        if (executingJob != null) {
            remaining = readJobRemainingAmount(executingJob);
            total = readJobFinalOutputTotal(executingJob);
        }

        if (total == null && status != null && status.crafting() != null) {
            total = status.crafting().amount();
        }

        if (total != null && total > 0 && remaining != null) {
            long clampedRemaining = Math.max(0L, Math.min(remaining, total));
            long done = total - clampedRemaining;
            summary.put("outputTotal", total);
            summary.put("outputDone", done);
            summary.put("outputRemaining", clampedRemaining);
        }

        return summary;
    }

    private static Long readJobRemainingAmount(Object executingJob) {
        try {
            Field remainingField = executingJob.getClass().getDeclaredField("remainingAmount");
            remainingField.setAccessible(true);
            return remainingField.getLong(executingJob);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Long readJobFinalOutputTotal(Object executingJob) {
        try {
            Field finalOutputField = executingJob.getClass().getDeclaredField("finalOutput");
            finalOutputField.setAccessible(true);
            Object finalOutput = finalOutputField.get(executingJob);
            if (!(finalOutput instanceof GenericStack stack)) {
                return null;
            }
            return stack.amount();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static List<Map<String, Object>> buildEntries(Object craftingLogic) {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (craftingLogic == null) {
            return entries;
        }

        KeyCounter all = new KeyCounter();
        Method getAllItems;
        Method getStored;
        Method getWaitingFor;
        Method getPendingOutputs;
        try {
            getAllItems = craftingLogic.getClass().getMethod("getAllItems", KeyCounter.class);
            getStored = craftingLogic.getClass().getMethod("getStored", AEKey.class);
            getWaitingFor = craftingLogic.getClass().getMethod("getWaitingFor", AEKey.class);
            getPendingOutputs = craftingLogic.getClass().getMethod("getPendingOutputs", AEKey.class);
        } catch (ReflectiveOperationException e) {
            return entries;
        }

        try {
            getAllItems.invoke(craftingLogic, all);
        } catch (ReflectiveOperationException e) {
            return entries;
        }

        PrerenderStorageService storage = PrerenderStorageService.getInstance();
        for (Object2LongMap.Entry<AEKey> entry : all) {
            AEKey key = entry.getKey();
            if (key == null) {
                continue;
            }

            long total = entry.getLongValue();
            long stored = invokeLong(getStored, craftingLogic, key);
            long waiting = invokeLong(getWaitingFor, craftingLogic, key);
            long active = invokeLong(getPendingOutputs, craftingLogic, key);

            if (stored <= 0 && waiting <= 0 && active <= 0) {
                continue;
            }

            long stackAmount = total > 0 ? total : stored + waiting + active;
            if (stackAmount <= 0) {
                stackAmount = 1;
            }

            Map<String, Object> row = new HashMap<>();
            row.put("stack", CpuHandler.genericStackToJson(new GenericStack(key, stackAmount), storage));
            row.put("stored", stored);
            row.put("waiting", waiting);
            row.put("active", active);
            entries.add(row);
        }

        return entries;
    }

    private static List<Map<String, Object>> buildPatterns(Object executingJob) {
        List<Map<String, Object>> patterns = new ArrayList<>();
        if (executingJob == null) {
            return patterns;
        }

        Map<?, ?> tasks = readJobTasks(executingJob);
        if (tasks == null || tasks.isEmpty()) {
            return patterns;
        }

        PrerenderStorageService storage = PrerenderStorageService.getInstance();
        for (Map.Entry<?, ?> entry : tasks.entrySet()) {
            if (!(entry.getKey() instanceof IPatternDetails pattern)) {
                continue;
            }

            Long remainingRuns = readTaskRemainingRuns(entry.getValue());
            if (remainingRuns == null || remainingRuns <= 0) {
                continue;
            }

            Map<String, Object> obj = new HashMap<>();
            obj.put("patternId", IconKeyUtils.iconKey(pattern.getDefinition()));
            obj.put("remainingRuns", remainingRuns);

            List<Map<String, Object>> outputs = new ArrayList<>();
            for (GenericStack output : pattern.getOutputs()) {
                if (output == null) {
                    continue;
                }
                outputs.add(CpuHandler.genericStackToJson(output, storage));
            }
            obj.put("outputs", outputs);

            List<Map<String, Object>> inputs = new ArrayList<>();
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                if (input == null) {
                    continue;
                }
                Map<String, Object> inputObj = new HashMap<>();
                inputObj.put("multiplier", input.getMultiplier());

                List<Map<String, Object>> choices = new ArrayList<>();
                for (GenericStack choice : input.getPossibleInputs()) {
                    if (choice == null) {
                        continue;
                    }
                    choices.add(CpuHandler.genericStackToJson(choice, storage));
                }
                inputObj.put("choices", choices);
                inputs.add(inputObj);
            }
            obj.put("inputs", inputs);

            patterns.add(obj);
        }

        return patterns;
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> readJobTasks(Object executingJob) {
        try {
            Field tasksField = executingJob.getClass().getDeclaredField("tasks");
            tasksField.setAccessible(true);
            Object tasks = tasksField.get(executingJob);
            if (tasks instanceof Map<?, ?> map) {
                return map;
            }
            return null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Long readTaskRemainingRuns(Object taskProgress) {
        if (taskProgress == null) {
            return null;
        }
        try {
            Field valueField = taskProgress.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            return valueField.getLong(taskProgress);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static long invokeLong(Method method, Object target, AEKey key) {
        try {
            Object value = method.invoke(target, key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            return 0L;
        } catch (ReflectiveOperationException e) {
            return 0L;
        }
    }
}
