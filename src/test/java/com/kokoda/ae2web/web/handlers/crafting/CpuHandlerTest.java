package com.kokoda.ae2web.web.handlers.crafting;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CpuHandlerTest {
    @Test
    void cpuToJsonIncludesOutputProgressWhenRemainingAmountAvailable() throws Exception {
        var status = new CraftingJobStatus(null, Integer.MAX_VALUE, 1, 0);
        ICraftingCPU cpu = new DummyCpu(status, 40, 100);

        Method cpuToJson = CpuHandler.class.getDeclaredMethod("cpuToJson", ICraftingCPU.class);
        cpuToJson.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> json = (Map<String, Object>) cpuToJson.invoke(null, cpu);

        @SuppressWarnings("unchecked")
        Map<String, Object> jobStatus = (Map<String, Object>) json.get("jobStatus");
        assertThat(jobStatus).isNotNull();
        assertThat(jobStatus.get("outputTotal")).isEqualTo(100L);
        assertThat(jobStatus.get("outputDone")).isEqualTo(60L);
        assertThat(jobStatus.get("outputRemaining")).isEqualTo(40L);
    }

    private static final class DummyCpu implements ICraftingCPU {
        public final DummyCraftingLogic craftingLogic;
        private final CraftingJobStatus status;

        private DummyCpu(CraftingJobStatus status, long remainingAmount, long totalAmount) {
            this.status = status;
            this.craftingLogic = new DummyCraftingLogic(remainingAmount, totalAmount);
        }

        @Override
        public boolean isBusy() {
            return true;
        }

        @Override
        public CraftingJobStatus getJobStatus() {
            return status;
        }

        @Override
        public void cancelJob() {
        }

        @Override
        public long getAvailableStorage() {
            return 0;
        }

        @Override
        public int getCoProcessors() {
            return 0;
        }

        @Override
        public Component getName() {
            return null;
        }

        @Override
        public CpuSelectionMode getSelectionMode() {
            return CpuSelectionMode.ANY;
        }
    }

    private static final class DummyCraftingLogic {
        private final DummyExecutingCraftingJob job;

        private DummyCraftingLogic(long remainingAmount, long totalAmount) {
            this.job = new DummyExecutingCraftingJob(remainingAmount, totalAmount);
        }
    }

    private static final class DummyExecutingCraftingJob {
        @SuppressWarnings("unused")
        private final DummyFinalOutput finalOutput;
        private final long remainingAmount;

        private DummyExecutingCraftingJob(long remainingAmount, long totalAmount) {
            this.finalOutput = new DummyFinalOutput(totalAmount);
            this.remainingAmount = remainingAmount;
        }
    }

    private record DummyFinalOutput(long amount) {
    }
}
