package com.kokoda.ae2web.web.handlers.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.kokoda.ae2web.util.IconKeyUtils;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CpuProcessHandlerTest {
    @Test
    void buildSummaryIncludesOutputProgressWhenJobFieldsAvailable() throws Exception {
        Method buildSummary = CpuProcessHandler.class.getDeclaredMethod("buildSummary", CraftingJobStatus.class, Object.class);
        buildSummary.setAccessible(true);

        CraftingJobStatus status = new CraftingJobStatus(null, 10, 3, 123);
        DummyExecutingCraftingJob job = new DummyExecutingCraftingJob(40, 100);

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) buildSummary.invoke(null, status, job);

        assertThat(summary.get("totalItems")).isEqualTo(10L);
        assertThat(summary.get("progress")).isEqualTo(3L);
        assertThat(summary.get("elapsedTimeNanos")).isEqualTo(123L);
        assertThat(summary.get("outputTotal")).isEqualTo(100L);
        assertThat(summary.get("outputDone")).isEqualTo(60L);
        assertThat(summary.get("outputRemaining")).isEqualTo(40L);
    }

    @Test
    void buildEntriesReturnsStoredWaitingActiveColumns() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PrerenderStorageService.getInstance().initialize();

        Method buildEntries = CpuProcessHandler.class.getDeclaredMethod("buildEntries", Object.class);
        buildEntries.setAccessible(true);

        AEItemKey key = AEItemKey.of(Items.STONE);
        DummyCraftingLogic logic = new DummyCraftingLogic(key, 10, 5, 2);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) buildEntries.invoke(null, logic);

        assertThat(entries).hasSize(1);
        Map<String, Object> row = entries.get(0);
        assertThat(row.get("stored")).isEqualTo(10L);
        assertThat(row.get("waiting")).isEqualTo(5L);
        assertThat(row.get("active")).isEqualTo(2L);

        @SuppressWarnings("unchecked")
        Map<String, Object> stack = (Map<String, Object>) row.get("stack");
        assertThat(stack.get("iconKey")).isEqualTo(IconKeyUtils.iconKey(key));
    }

    @Test
    void buildPatternsReturnsPatternOutputsInputsAndRemainingRuns() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PrerenderStorageService.getInstance().initialize();

        Method buildPatterns = CpuProcessHandler.class.getDeclaredMethod("buildPatterns", Object.class);
        buildPatterns.setAccessible(true);

        AEItemKey patternDef = AEItemKey.of(Items.CRAFTING_TABLE);
        AEItemKey outputKey = AEItemKey.of(Items.DIAMOND);
        AEItemKey inputKey = AEItemKey.of(Items.STICK);

        DummyPatternDetails pattern = new DummyPatternDetails(
                patternDef,
                new GenericStack[]{new GenericStack(outputKey, 1)},
                new IPatternDetails.IInput[]{new DummyInput(new GenericStack[]{new GenericStack(inputKey, 2)}, 1)}
        );

        DummyExecutingCraftingJobWithTasks job = new DummyExecutingCraftingJobWithTasks(Map.of(pattern, new DummyTaskProgress(7)));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> patterns = (List<Map<String, Object>>) buildPatterns.invoke(null, job);

        assertThat(patterns).hasSize(1);
        Map<String, Object> obj = patterns.get(0);
        assertThat(obj.get("patternId")).isEqualTo(IconKeyUtils.iconKey(patternDef));
        assertThat(obj.get("remainingRuns")).isEqualTo(7L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outputs = (List<Map<String, Object>>) obj.get("outputs");
        assertThat(outputs).hasSize(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> inputs = (List<Map<String, Object>>) obj.get("inputs");
        assertThat(inputs).hasSize(1);
        assertThat(inputs.get(0).get("multiplier")).isEqualTo(1L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) inputs.get(0).get("choices");
        assertThat(choices).hasSize(1);
        assertThat(choices.get(0).get("iconKey")).isEqualTo(IconKeyUtils.iconKey(inputKey));
    }

    private static final class DummyCraftingLogic {
        private final AEItemKey key;
        private final long stored;
        private final long waiting;
        private final long active;

        private DummyCraftingLogic(AEItemKey key, long stored, long waiting, long active) {
            this.key = key;
            this.stored = stored;
            this.waiting = waiting;
            this.active = active;
        }

        @SuppressWarnings("unused")
        public void getAllItems(KeyCounter counter) {
            counter.add(key, stored + waiting + active);
        }

        @SuppressWarnings("unused")
        public long getStored(appeng.api.stacks.AEKey what) {
            return stored;
        }

        @SuppressWarnings("unused")
        public long getWaitingFor(appeng.api.stacks.AEKey what) {
            return waiting;
        }

        @SuppressWarnings("unused")
        public long getPendingOutputs(appeng.api.stacks.AEKey what) {
            return active;
        }
    }

    private static final class DummyExecutingCraftingJob {
        @SuppressWarnings("unused")
        private final GenericStack finalOutput;
        @SuppressWarnings("unused")
        private final long remainingAmount;

        private DummyExecutingCraftingJob(long remainingAmount, long totalAmount) {
            this.finalOutput = new GenericStack(AEItemKey.of(Items.DIRT), totalAmount);
            this.remainingAmount = remainingAmount;
        }
    }

    private static final class DummyExecutingCraftingJobWithTasks {
        @SuppressWarnings("unused")
        private final Map<IPatternDetails, DummyTaskProgress> tasks;

        private DummyExecutingCraftingJobWithTasks(Map<IPatternDetails, DummyTaskProgress> tasks) {
            this.tasks = tasks;
        }
    }

    private static final class DummyTaskProgress {
        @SuppressWarnings("unused")
        private final long value;

        private DummyTaskProgress(long value) {
            this.value = value;
        }
    }

    private static final class DummyInput implements IPatternDetails.IInput {
        private final GenericStack[] possible;
        private final long multiplier;

        private DummyInput(GenericStack[] possible, long multiplier) {
            this.possible = possible;
            this.multiplier = multiplier;
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return possible;
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(appeng.api.stacks.AEKey input, net.minecraft.world.level.Level level) {
            return true;
        }

        @Override
        public appeng.api.stacks.AEKey getRemainingKey(appeng.api.stacks.AEKey template) {
            return null;
        }
    }

    private static final class DummyPatternDetails implements IPatternDetails {
        private final AEItemKey definition;
        private final GenericStack[] outputs;
        private final IInput[] inputs;

        private DummyPatternDetails(AEItemKey definition, GenericStack[] outputs, IInput[] inputs) {
            this.definition = definition;
            this.outputs = outputs;
            this.inputs = inputs;
        }

        @Override
        public AEItemKey getDefinition() {
            return definition;
        }

        @Override
        public IInput[] getInputs() {
            return inputs;
        }

        @Override
        public GenericStack[] getOutputs() {
            return outputs;
        }
    }
}
