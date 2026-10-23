package com.lelqwq.gtpoc.mixins;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.lelqwq.gtpoc.Config;

import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.common.tileentities.machines.basic.MTEIndustrialApiary;

/** GT5U 5.09.51.482：仅修改工业蜂箱的速度，保留原版耗电计算。 */
@Mixin(value = MTEIndustrialApiary.class, remap = false)
public class MixinIndustrialApiary {

    @Shadow
    public int mSpeed;

    // actualdivider 负责耗时和溢出产物倍率；useddivider 继续以 2^等级计算耗电。
    @ModifyVariable(method = "checkRecipe", at = @At(value = "STORE", ordinal = 0), name = "actualdivider", require = 1)
    private int gtpoc$productionAcceleration(int acceleration) {
        return Config.enableLosslessApiary ? acceleration * acceleration : acceleration;
    }

    // 第四次写入是繁殖耗时；原版 useddivider 仍供后续耗电计算使用。
    @Redirect(
        method = "checkRecipe",
        at = @At(
            value = "FIELD",
            target = "Lgregtech/common/tileentities/machines/basic/MTEIndustrialApiary;mMaxProgresstime:I",
            opcode = Opcodes.PUTFIELD,
            ordinal = 3),
        require = 1)
    private void gtpoc$breedingDuration(@Coerce MTEBasicMachine apiary, int duration) {
        apiary.mMaxProgresstime = Config.enableLosslessApiary ? Math.max(1, 100 / (1 << (2 * mSpeed))) : duration;
    }

    // 482 的速度按钮与悬浮提示使用独立 lambda；不修改耗电提示共用的 getAcceleration()。
    @ModifyArg(
        method = "lambda$addUIWidgets$26",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;valueOf(I)Ljava/lang/String;"),
        index = 0,
        require = 1)
    private int gtpoc$speedLabel(int acceleration) {
        return Config.enableLosslessApiary ? acceleration * acceleration : acceleration;
    }

    @ModifyArg(
        method = "lambda$addUIWidgets$19",
        at = @At(value = "INVOKE", target = "Ljava/lang/Integer;valueOf(I)Ljava/lang/Integer;"),
        index = 0,
        require = 1)
    private int gtpoc$speedTooltip(int acceleration) {
        return Config.enableLosslessApiary ? acceleration * acceleration : acceleration;
    }
}
