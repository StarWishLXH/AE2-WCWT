package com.lhy.wcwt.gtlcore.mixin;

import appeng.api.stacks.GenericStack;
import com.lhy.wcwt.gtlcore.GtlcoreEncodingBridge;
import com.lhy.wcwt.pull.WcwtIngredientPriorities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = WcwtIngredientPriorities.class, remap = false)
public abstract class WcwtIngredientPrioritiesMixin {
    @Inject(
            method = "chooseBestGenericStack(Lcom/lhy/wcwt/pull/WcwtIngredientPriorities$PriorityContext;Ljava/util/List;)Lappeng/api/stacks/GenericStack;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void wcwtGtlcore$preferUniversalCircuitGeneric(
            WcwtIngredientPriorities.PriorityContext context,
            List<GenericStack> candidates,
            CallbackInfoReturnable<GenericStack> cir) {
        GenericStack universalCircuit = GtlcoreEncodingBridge.chooseUniversalCircuit(context, candidates);
        if (universalCircuit != null) {
            cir.setReturnValue(universalCircuit);
        }
    }

    @Inject(
            method = "chooseBestItemForEncoding(Lcom/lhy/wcwt/pull/WcwtIngredientPriorities$PriorityContext;Lnet/minecraft/world/item/crafting/Ingredient;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void wcwtGtlcore$preferUniversalCircuitItem(
            WcwtIngredientPriorities.PriorityContext context,
            Ingredient ingredient,
            CallbackInfoReturnable<ItemStack> cir) {
        ItemStack universalCircuit = GtlcoreEncodingBridge.chooseUniversalCircuit(context, ingredient);
        if (!universalCircuit.isEmpty()) {
            cir.setReturnValue(universalCircuit);
        }
    }
}
