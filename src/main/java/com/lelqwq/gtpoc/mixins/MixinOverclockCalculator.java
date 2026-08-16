package com.lelqwq.gtpoc.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lelqwq.gtpoc.Config;

import gregtech.api.util.OverclockCalculator;

/**
 * A1 一刀切策略：在 GT5U 超频计算入口处，把时长因子强制为 4.0。
 *
 * <p>
 * 效果：每级超频 时间÷4、峰值 EU/t ×4（eutIncreasePerOC 保持默认 4）→ 单配方总耗能不变，
 * 即 EBF 式无损超频。覆盖所有经 {@link OverclockCalculator} 计算超频的机器：
 * 单块机（MTEBasicMachine 系）、ProcessingLogic 多方块、以及 GT++ 等直接调用该计算器的第三方机器。
 *
 * <p>
 * 两个注入点：{@code calculate()} 是常规配方检查路径；{@code calculateMultiplierUnderOneTick()}
 * 是装配线/多方块炉等子 tick 并行倍率路径，读取同一字段，必须同步强制以保持数学一致。
 *
 * <p>
 * 注意：两个目标方法都有返回值（分别返回 OverclockCalculator / double），注入回调
 * <b>必须</b>用 {@code CallbackInfoReturnable<...>}；误用 {@code CallbackInfo} 不会被编译期
 * 注解处理器发现，而是在运行时 APPLY 阶段抛 InvalidInjectionException，导致目标类整体
 * transform 失败（表现为游戏内 NoClassDefFoundError / 崩溃）。
 */
@Mixin(value = OverclockCalculator.class, remap = false)
public class MixinOverclockCalculator {

    @Inject(method = "calculate", at = @At("HEAD"), remap = false)
    private void gtpoc$forcePerfectOC(CallbackInfoReturnable<OverclockCalculator> cir) {
        if (Config.enablePerfectOverclock) {
            ((OverclockCalculator) (Object) this).setDurationDecreasePerOC(4.0);
        }
    }

    @Inject(method = "calculateMultiplierUnderOneTick", at = @At("HEAD"), remap = false)
    private void gtpoc$forcePerfectOCSubTick(CallbackInfoReturnable<Double> cir) {
        if (Config.enablePerfectOverclock) {
            ((OverclockCalculator) (Object) this).setDurationDecreasePerOC(4.0);
        }
    }
}
