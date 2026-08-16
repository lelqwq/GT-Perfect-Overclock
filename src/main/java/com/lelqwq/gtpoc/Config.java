package com.lelqwq.gtpoc;

/**
 * 全局配置。Mixin 注入点在每次超频计算时读取。
 */
public final class Config {

    /** 总开关：是否强制所有机器无损超频。默认 true，保证配置加载前也生效。 */
    public static boolean enablePerfectOverclock = true;

    private Config() {}
}
