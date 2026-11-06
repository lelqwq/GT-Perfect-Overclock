package com.lelqwq.gtpoc.mixins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lelqwq.gtpoc.Config;

import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.common.tileentities.machines.basic.MTEIndustrialApiary;

/**
 * GT5U 5.09.54.265：工业蜂箱速度升级改为 4^等级，同等级 EU/t 保持原版。
 *
 * <p>
 * 生产：只放大 local 变量 {@code actualDivider} 的初值（2^n → 4^n），耗电仍按原版
 * {@code usedDivider = 2^n} 计算。第 5 级起在原版机制之上追加产量补偿。
 *
 * <p>
 * 繁殖：改写第四次 {@code mMaxProgresstime} 写入，最低 1 tick，耗电仍按原版 2^n。
 *
 * <p>
 * 显示：2.9.0 起 UI 迁到 {@code MTEIndustrialApiaryGui}，速度按钮与悬浮提示
 * 统一读 {@code getAcceleration()}，所以改这一个方法即可同步显示。
 */
@Mixin(value = MTEIndustrialApiary.class, remap = false)
public class MixinIndustrialApiary {

    private static final Logger LOG = LogManager.getLogger("gtpoc");

    @Shadow
    private int mSpeed;

    // actualDivider 负责耗时和溢出产物倍率；usedDivider 继续以 2^等级计算耗电。
    @ModifyVariable(method = "checkRecipe", at = @At(value = "STORE", ordinal = 0), name = "actualDivider", require = 1)
    private int gtpoc$productionAcceleration(int acceleration) {
        return Config.enableLosslessApiary ? acceleration * acceleration : acceleration;
    }

    /**
     * 第 5 级起追加产量补偿。
     *
     * <p>
     * 第二次写入 {@code actualDivider} 时，它已经是原版的"产物倍率"
     * （{@code actualDivider /= Math.min(actualDivider, timeMaxDivider)}）：速度被
     * {@code timeMaxDivider} 截断后，超出的部分折算成每批产量。原版要到 7 级左右才触发，
     * 这里从 5 级起额外保证产量倍率至少为 4^(等级-4)（5 级 ×4、6 级 ×16、7 级 ×64、8 级 ×256）。
     * 只抬不压：原版机制算出的倍率更高时保持原值。
     */
    @ModifyVariable(method = "checkRecipe", at = @At(value = "STORE", ordinal = 1), name = "actualDivider", require = 1)
    private int gtpoc$yieldBoost(int divider) {
        if (!Config.enableLosslessApiary || mSpeed < 5) return divider;
        return Math.max(divider, 1 << (2 * (mSpeed - 4)));
    }

    // 第四次写入是繁殖耗时；原版 usedDivider 仍供后续耗电计算使用。
    @Redirect(
        method = "checkRecipe",
        at = @At(
            value = "FIELD",
            target = "Lgregtech/common/tileentities/machines/basic/MTEIndustrialApiary;mMaxProgresstime:I",
            opcode = Opcodes.PUTFIELD,
            ordinal = 3),
        require = 1)
    private void gtpoc$breedingDuration(@Coerce MTEBasicMachine apiary, int duration) {
        if (!Config.enableLosslessApiary) {
            apiary.mMaxProgresstime = duration;
            return;
        }
        // 原版此处必然是 100 / min(100, 2^等级)；不符说明 GT5U 改了方法体、注入位置已偏移，
        // 保守放弃改写并留下日志，避免把错误的时长写进机器。
        if (duration != 100 / Math.min(100, 1 << mSpeed)) {
            LOG.warn("[gtpoc] industrial apiary breeding injection offset detected, skipped");
            apiary.mMaxProgresstime = duration;
            return;
        }
        apiary.mMaxProgresstime = Math.max(1, 100 / (1 << (2 * mSpeed)));
    }

    /** GUI 的速度按钮与悬浮提示都读这里，按实际加速倍率显示。 */
    @Inject(method = "getAcceleration", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtpoc$displayAcceleration(CallbackInfoReturnable<Integer> cir) {
        if (Config.enableLosslessApiary) {
            cir.setReturnValue(1 << (2 * mSpeed));
        }
    }

    /** 耗电提示内部也用 getAcceleration() 估算附加耗电，必须让它继续拿到原版 2^等级。 */
    @Redirect(
        method = "getAdditionalEnergyUsage",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/basic/MTEIndustrialApiary;getAcceleration()I"),
        require = 1,
        remap = false)
    private int gtpoc$vanillaAccelerationForEnergy(MTEIndustrialApiary self) {
        return 1 << mSpeed;
    }
}
