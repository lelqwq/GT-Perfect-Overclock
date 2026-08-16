package com.lelqwq.gtpoc;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

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

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        Config.enablePerfectOverclock = config.getBoolean(
            "enablePerfectOverclock",
            Configuration.CATEGORY_GENERAL,
            true,
            "将所有使用 GT OverclockCalculator 的机器改为 EBF 式无损超频" + "（每级超频时间÷4、峰值功率×4、单配方总耗能不变）");
        if (config.hasChanged()) {
            config.save();
        }
    }
}
