package com.lelqwq.gtpoc;

/**
 * 全局配置。Mixin 注入点在每次超频计算时读取。
 */
public final class Config {

    /** 总开关：是否强制所有机器无损超频。默认 true，保证配置加载前也生效。 */
    public static boolean enablePerfectOverclock = true;

    /** 世界加速器：加速 ×4/档（封顶 512），耗电保持原版 —— 一分电一分速度。默认 true。 */
    public static boolean enableLosslessWA = true;

    /** 采矿场：挖速 ×4/档，耗电保持原版 —— 一分电一分速度。默认 true。 */
    public static boolean enableLosslessMiner = true;

    /** 工业蜂箱：速度升级 ×4/级，同等级耗电保持原版。默认 true。 */
    public static boolean enableLosslessApiary = true;

    private Config() {}
}
