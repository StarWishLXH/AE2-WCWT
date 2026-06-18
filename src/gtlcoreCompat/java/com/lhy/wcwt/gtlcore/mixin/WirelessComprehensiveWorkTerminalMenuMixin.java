package com.lhy.wcwt.gtlcore.mixin;

import appeng.api.stacks.GenericStack;
import com.lhy.wcwt.gtlcore.GtlcoreEncodingBridge;
import com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = WirelessComprehensiveWorkTerminalMenu.class, remap = false)
public abstract class WirelessComprehensiveWorkTerminalMenuMixin {
    @ModifyArg(
            method = "createProcessingPattern",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/crafting/PatternDetailsHelper;encodeProcessingPattern([Lappeng/api/stacks/GenericStack;[Lappeng/api/stacks/GenericStack;)Lnet/minecraft/world/item/ItemStack;"
            ),
            index = 0,
            remap = false
    )
    private GenericStack[] wcwtGtlcore$stripDataItemTags(GenericStack[] inputs) {
        return GtlcoreEncodingBridge.sanitizeProcessingInputs(inputs);
    }
}
