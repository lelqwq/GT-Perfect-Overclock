package com.lelqwq.gtpoc;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import gregtech.common.tileentities.machines.basic.MTEMiner;
import gregtech.common.tileentities.machines.basic.MTEWorldAccelerator;

/**
 * GT Perfect Overclock —— 把 GTNH 2.8.4 所有格雷机器改为 EBF 式无损超频。
 *
 * <p>
 * 实现方式：Mixin 注入 {@code gregtech.api.util.OverclockCalculator}，在每次超频计算入口
 * 强制时长因子为 4.0（见 {@code mixins.MixinOverclockCalculator}）。纯计算逻辑修改，
 * 无持久化数据，存档完全兼容；卸载 mod 即恢复原版行为。
 */
@Mod(
    modid = GTPerfectOverclock.MODID,
    name = GTPerfectOverclock.NAME,
    version = Tags.VERSION,
    dependencies = "required-after:gregtech")
public class GTPerfectOverclock {

    public static final String MODID = "gtpoc";
    public static final String NAME = "GT Perfect Overclock";

    /**
     * WA 无损加速表：4^档（一分电一分速度），封顶 512 = 原版性能上限。
     * 档位 0-4 严格与耗电曲线（V≈4^档）对齐：每加速 tick 耗能恒等于 T0 基准；
     * 档位 5+ 速度封顶不再增长（耗电仍随档位涨），WA 升到 EV（档位 4）即可。
     */
    private static final int[] LOSSLESS_ACCELERATE = { 1, 4, 16, 64, 256, 512, 512, 512, 512, 512, 512, 512, 512, 512,
        512, 512 };

    /** 原版 WA 加速表快照（首次 preInit 时备份），开关关闭时还原。 */
    private static int[] originalAccelerate;

    /**
     * 采矿场无损速度表（每方块耗时 tick）：×4/档，与耗电曲线（ENERGY≈×4/档）对齐 —— 一分电一分速度。
     * 每方块耗能 = SPEED×ENERGY：T0/T1=1280、T2=1280、T3=1280、T4=2560。
     * T4 取 5t 而非 2.5t：DrillingLogicDelegate 有 `aTick % (getMachineSpeed()/5)`，
     * SPEED&lt;5 会导致除零崩溃，故顶档非严格 ×4（仍为原版 10240 的 1/4）。
     */
    private static final int[] LOSSLESS_MINER_SPEED = { 160, 160, 40, 10, 5 };

    /** 原版采矿场速度表快照，开关关闭时还原。 */
    private static int[] originalMinerSpeed;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        Config.enablePerfectOverclock = config.getBoolean(
            "enablePerfectOverclock",
            Configuration.CATEGORY_GENERAL,
            true,
            "将所有使用 GT OverclockCalculator 的机器改为 EBF 式无损超频" + "（每级超频时间÷4、峰值功率×4、单配方总耗能不变）");
        Config.enableLosslessWA = config.getBoolean(
            "enableLosslessWA",
            Configuration.CATEGORY_GENERAL,
            true,
            "世界加速器无损化：加速 ×4/档（封顶 512），耗电保持原版 —— 一分电一分速度");
        Config.enableLosslessMiner = config.getBoolean(
            "enableLosslessMiner",
            Configuration.CATEGORY_GENERAL,
            true,
            "采矿场无损化：挖速 ×4/档，耗电保持原版 —— 一分电一分速度");
        Config.enableLosslessApiary = config.getBoolean(
            "enableLosslessApiary",
            Configuration.CATEGORY_GENERAL,
            true,
            "工业蜂箱无损化：速度升级 ×4/级，同等级 EU/t 保持原版；繁殖最低 1 tick");
        // 速度表在 GT5U preInit（机器注册）之后、本 mod preInit 时改写；快照原版表供开关关闭时还原
        if (originalAccelerate == null) {
            originalAccelerate = MTEWorldAccelerator.mAccelerateStatic.clone();
        }
        if (originalMinerSpeed == null) {
            originalMinerSpeed = getMinerSpeedTable().clone();
        }
        applyWorldAcceleratorSpeedTable();
        applyMinerSpeedTable();
        if (config.hasChanged()) {
            config.save();
        }
    }

    /**
     * 读取采矿场 SPEED 表（private static final 数组，引用 final 但元素可写）。
     * 反射只用于取数组引用（Java 25 下类路径类允许 setAccessible 读取），元素写入是普通数组赋值。
     * 反射失败时返回空数组，改写逻辑自动跳过（不崩溃）。
     */
    private static int[] getMinerSpeedTable() {
        try {
            java.lang.reflect.Field field = MTEMiner.class.getDeclaredField("SPEED");
            field.setAccessible(true);
            return (int[]) field.get(null);
        } catch (Exception e) {
            return new int[0];
        }
    }

    /** 把采矿场 SPEED 表替换为无损表（或按开关还原原版表）。 */
    private static void applyMinerSpeedTable() {
        int[] speed = getMinerSpeedTable();
        int[] table = Config.enableLosslessMiner ? LOSSLESS_MINER_SPEED : originalMinerSpeed;
        for (int i = 0; i < speed.length && i < table.length; i++) {
            speed[i] = table[i];
        }
    }

    /**
     * 把 WA 加速表替换为无损表（或按开关还原原版表）。
     * 数组元素本身可变（仅引用 final），直接写入即可，无需反射。
     */
    private static void applyWorldAcceleratorSpeedTable() {
        int[] table = Config.enableLosslessWA ? LOSSLESS_ACCELERATE : originalAccelerate;
        for (int i = 0; i < MTEWorldAccelerator.mAccelerateStatic.length && i < table.length; i++) {
            MTEWorldAccelerator.mAccelerateStatic[i] = table[i];
        }
    }
}
