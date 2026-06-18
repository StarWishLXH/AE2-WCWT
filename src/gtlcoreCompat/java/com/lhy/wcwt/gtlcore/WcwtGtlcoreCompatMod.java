package com.lhy.wcwt.gtlcore;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(WcwtGtlcoreCompatMod.MOD_ID)
public final class WcwtGtlcoreCompatMod {
    public static final String MOD_ID = "wcwt_gtlcore_compat";
    private static final Logger LOGGER = LogUtils.getLogger();

    public WcwtGtlcoreCompatMod() {
        LOGGER.info("WCWT GTLCore Compat loaded; debug logging is {}",
                Boolean.getBoolean("wcwt.gtlcoreCompat.debug") ? "enabled" : "disabled");
    }
}
