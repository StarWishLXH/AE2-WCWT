package com.lhy.wcwt.gtlcore.mixin;

import appeng.api.stacks.GenericStack;
import com.lhy.wcwt.compat.emi.WcwtEmiRecipeHandler;
import com.lhy.wcwt.gtlcore.GtlcoreEncodingBridge;
import com.lhy.wcwt.gtlcore.GtlcoreTransferBridge;
import com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu;
import com.lhy.wcwt.pull.WcwtIngredientPriorities;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

@Mixin(value = WcwtEmiRecipeHandler.class, remap = false)
public abstract class WcwtEmiRecipeHandlerMixin {
    private static final ThreadLocal<Object> WCWT_GTLCORE_CURRENT_RECIPE = new ThreadLocal<>();
    private static final ThreadLocal<GtlcoreTransferBridge.MultiblockInputFilter> WCWT_GTLCORE_CURRENT_INPUT_FILTER =
            new ThreadLocal<>();

    @Shadow(remap = false)
    private static @Nullable GenericStack toGenericStack(EmiStack stack, long amount) {
        throw new AssertionError();
    }

    @Inject(method = "craft", at = @At("HEAD"), remap = false)
    private void wcwtGtlcore$captureRecipe(EmiRecipe recipe,
                                           EmiCraftContext<WirelessComprehensiveWorkTerminalMenu> context,
                                           CallbackInfoReturnable<Boolean> cir) {
        WCWT_GTLCORE_CURRENT_RECIPE.set(recipe);
        WCWT_GTLCORE_CURRENT_INPUT_FILTER.set(GtlcoreTransferBridge.createMultiblockInputFilter(recipe));
    }

    @Inject(method = "craft", at = @At("RETURN"), remap = false)
    private void wcwtGtlcore$clearRecipe(EmiRecipe recipe,
                                         EmiCraftContext<WirelessComprehensiveWorkTerminalMenu> context,
                                         CallbackInfoReturnable<Boolean> cir) {
        WCWT_GTLCORE_CURRENT_RECIPE.remove();
        WCWT_GTLCORE_CURRENT_INPUT_FILTER.remove();
    }

    @Inject(
            method = "toGenericStack(Lcom/lhy/wcwt/pull/WcwtIngredientPriorities$PriorityContext;Ldev/emi/emi/api/stack/EmiIngredient;)Lappeng/api/stacks/GenericStack;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void wcwtGtlcore$dropFilteredMultiblockInputSlot(
            WcwtIngredientPriorities.PriorityContext priorityContext,
            EmiIngredient ingredient,
            CallbackInfoReturnable<GenericStack> cir) {
        GtlcoreTransferBridge.MultiblockInputFilter filter = WCWT_GTLCORE_CURRENT_INPUT_FILTER.get();
        if (filter == null || ingredient == null || ingredient.isEmpty()) {
            return;
        }

        List<GenericStack> candidates = ingredient.getEmiStacks().stream()
                .map(stack -> toGenericStack(stack, ingredient.getAmount()))
                .filter(Objects::nonNull)
                .toList();
        if (filter.shouldDropCandidateGroup(candidates)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(
            method = "toGenericStack(Lcom/lhy/wcwt/pull/WcwtIngredientPriorities$PriorityContext;Ldev/emi/emi/api/stack/EmiIngredient;)Lappeng/api/stacks/GenericStack;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void wcwtGtlcore$avoidSkippedTransferCandidate(
            WcwtIngredientPriorities.PriorityContext priorityContext,
            EmiIngredient ingredient,
            CallbackInfoReturnable<GenericStack> cir) {
        GenericStack selected = cir.getReturnValue();
        if (!GtlcoreEncodingBridge.shouldSkipTransferCandidate(selected)) {
            return;
        }

        List<GenericStack> candidates = ingredient.getEmiStacks().stream()
                .map(stack -> toGenericStack(stack, ingredient.getAmount()))
                .filter(Objects::nonNull)
                .filter(candidate -> !GtlcoreEncodingBridge.shouldSkipTransferCandidate(candidate))
                .toList();
        cir.setReturnValue(WcwtIngredientPriorities.chooseBestGenericStack(priorityContext, candidates));
    }

    @ModifyArg(
            method = "craft",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/lhy/wcwt/network/JeiCraftingTransferPacket;<init>(Ljava/util/List;Ljava/util/List;ZLappeng/parts/encoding/EncodingMode;)V"
            ),
            index = 0,
            remap = false
    )
    @SuppressWarnings("rawtypes")
    private List wcwtGtlcore$applyGtlcoreInputFilter(List inputs) {
        return GtlcoreEncodingBridge.filterTransferInputs(inputs);
    }

    @ModifyArg(
            method = "craft",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/lhy/wcwt/network/JeiCraftingTransferPacket;<init>(Ljava/util/List;Ljava/util/List;ZLappeng/parts/encoding/EncodingMode;)V"
            ),
            index = 1,
            remap = false
    )
    @SuppressWarnings("rawtypes")
    private List wcwtGtlcore$applyGtlcoreOutputFilter(List outputs) {
        return GtlcoreTransferBridge.applyOutputImport(WCWT_GTLCORE_CURRENT_RECIPE.get(), outputs);
    }
}
