package com.lelqwq.gtpoc.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lelqwq.gtpoc.Config;

import gregtech.api.util.OverclockCalculator;

/**
 * 在 GT5U 超频计算入口处，把"有损"的时长因子提升为 4.0。
 *
 * <p>
 * 效果：每级超频 时间÷4、峰值 EU/t ×4（eutIncreasePerOC 保持调用方设定）→ 单配方总耗能不变，
 * 即 EBF 式无损超频。覆盖所有经 {@link OverclockCalculator} 计算超频的机器：
 * 单块机（MTEBasicMachine 系）、ProcessingLogic 多方块、以及第三方机器。
 *
 * <p>
 * 只提升、不压低：原版时长因子小于 4 的机器（默认 2.0、PCB 厂的 2.0、纳米锻造炉的 2.0 等）
 * 提升到 4.0；已经大于或等于 4 的保持原值，避免削弱工业电弧炉 YBCO 电极（6）、
 * 宇宙素纳米电极（8）这类本来就更快的机器。
 *
 * <p>
 * 两个注入点：{@code calculate()} 是常规配方检查路径；{@code calculateMultiplierUnderOneTick()}
 * 是装配线/多方块炉等子 tick 并行倍率路径，读取同一字段，必须同步处理以保持数学一致。
 *
 * <p>
 * 注意：两个目标方法都有返回值（分别返回 OverclockCalculator / double），注入回调
 * <b>必须</b>用 {@code CallbackInfoReturnable<...>}；误用 {@code CallbackInfo} 不会被编译期
 * 注解处理器发现，而是在运行时 APPLY 阶段抛 InvalidInjectionException，导致目标类整体
 * transform 失败（表现为游戏内 NoClassDefFoundError / 崩溃）。
 */
@Mixin(value = OverclockCalculator.class, remap = false)
public class MixinOverclockCalculator {

    /** 原版时长因子：默认 2.0，调用方可显式覆盖。 */
    @Shadow
    protected double durationDecreasePerOC;

    @Inject(method = "calculate", at = @At("HEAD"), remap = false)
    private void gtpoc$forcePerfectOC(CallbackInfoReturnable<OverclockCalculator> cir) {
        gtpoc$applyPerfectOC();
    }

    @Inject(method = "calculateMultiplierUnderOneTick", at = @At("HEAD"), remap = false)
    private void gtpoc$forcePerfectOCSubTick(CallbackInfoReturnable<Double> cir) {
        gtpoc$applyPerfectOC();
    }

    /**
     * 因子 &lt; 4 时提升到 4（有损 → 无损）；因子 ≥ 4 时保持不动。
     *
     * <p>
     * 之所以不再"一刀切无条件设 4.0"：2.9.0 的工业电弧炉电极表里存在 6 和 8 的因子，
     * 无条件覆盖会把它们压到 4，反而比原版更慢。
     */
    private void gtpoc$applyPerfectOC() {
        if (!Config.enablePerfectOverclock) return;
        if (durationDecreasePerOC < 4.0) {
            ((OverclockCalculator) (Object) this).setDurationDecreasePerOC(4.0);
        }
    }
}
