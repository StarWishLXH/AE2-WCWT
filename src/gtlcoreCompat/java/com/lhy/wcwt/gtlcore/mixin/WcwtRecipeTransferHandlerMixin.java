package com.lhy.wcwt.gtlcore.mixin;

import appeng.api.stacks.GenericStack;
import com.lhy.wcwt.compat.jei.WcwtRecipeTransferHandler;
import com.lhy.wcwt.gtlcore.GtlcoreEncodingBridge;
import com.lhy.wcwt.gtlcore.GtlcoreTransferBridge;
import com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu;
import com.lhy.wcwt.pull.WcwtIngredientPriorities;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import net.minecraft.world.entity.player.Player;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

@Mixin(value = WcwtRecipeTransferHandler.class, remap = false)
public abstract class WcwtRecipeTransferHandlerMixin {
    private static final ThreadLocal<Object> WCWT_GTLCORE_CURRENT_RECIPE = new ThreadLocal<>();
    private static final ThreadLocal<GtlcoreTransferBridge.MultiblockInputFilter> WCWT_GTLCORE_CURRENT_INPUT_FILTER =
            new ThreadLocal<>();

    @Shadow(remap = false)
    private static @Nullable GenericStack toGenericStack(@Nullable ITypedIngredient<?> ingredient,
                                                         boolean preserveItemAmounts) {
        throw new AssertionError();
    }

    @Inject(method = "transferRecipe", at = @At("HEAD"), remap = false)
    private void wcwtGtlcore$captureRecipe(WirelessComprehensiveWorkTerminalMenu menu,
                                           Object recipe,
                                           IRecipeSlotsView recipeSlots,
                                           Player player,
                                           boolean maxTransfer,
                                           boolean doTransfer,
                                           CallbackInfoReturnable<IRecipeTransferError> cir) {
        WCWT_GTLCORE_CURRENT_RECIPE.set(recipe);
        WCWT_GTLCORE_CURRENT_INPUT_FILTER.set(doTransfer
                ? GtlcoreTransferBridge.createMultiblockInputFilter(recipe)
                : null);
        GtlcoreTransferBridge.traceTransferStart(recipe, maxTransfer, doTransfer);
    }

    @Inject(method = "transferRecipe", at = @At("RETURN"), remap = false)
    private void wcwtGtlcore$clearRecipe(WirelessComprehensiveWorkTerminalMenu menu,
                                         Object recipe,
                                         IRecipeSlotsView recipeSlots,
                                         Player player,
                                         boolean maxTransfer,
                                         boolean doTransfer,
                                         CallbackInfoReturnable<IRecipeTransferError> cir) {
        WCWT_GTLCORE_CURRENT_RECIPE.remove();
        WCWT_GTLCORE_CURRENT_INPUT_FILTER.remove();
    }

    @Inject(method = "toPreferredGenericStack", at = @At("HEAD"), cancellable = true, remap = false)
    private static void wcwtGtlcore$dropFilteredMultiblockInputSlot(
            WcwtIngredientPriorities.PriorityContext priorityContext,
            IRecipeSlotView slotView,
            boolean preserveItemAmounts,
            CallbackInfoReturnable<GenericStack> cir) {
        GtlcoreTransferBridge.MultiblockInputFilter filter = WCWT_GTLCORE_CURRENT_INPUT_FILTER.get();
        if (filter == null) {
            return;
        }

        List<GenericStack> candidates = slotView.getAllIngredients()
                .map(ingredient -> toGenericStack(ingredient, preserveItemAmounts))
                .filter(Objects::nonNull)
                .toList();
        if (filter.shouldDropCandidateGroup(candidates)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "toPreferredGenericStack", at = @At("RETURN"), cancellable = true, remap = false)
    private static void wcwtGtlcore$avoidSkippedTransferCandidate(
            WcwtIngredientPriorities.PriorityContext priorityContext,
            IRecipeSlotView slotView,
            boolean preserveItemAmounts,
            CallbackInfoReturnable<GenericStack> cir) {
        GenericStack selected = cir.getReturnValue();
        if (!GtlcoreEncodingBridge.shouldSkipTransferCandidate(selected)) {
            return;
        }

        List<GenericStack> candidates = slotView.getAllIngredients()
                .map(ingredient -> toGenericStack(ingredient, preserveItemAmounts))
                .filter(Objects::nonNull)
                .filter(candidate -> !GtlcoreEncodingBridge.shouldSkipTransferCandidate(candidate))
                .toList();
        cir.setReturnValue(WcwtIngredientPriorities.chooseBestGenericStack(priorityContext, candidates));
    }

    @ModifyExpressionValue(
            method = "transferRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/lhy/wcwt/compat/jei/WcwtRecipeTransferHandler;collectProcessingOutputs(Lnet/minecraft/world/item/crafting/Recipe;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;)Ljava/util/List;"
            ),
            remap = false
    )
    @SuppressWarnings("rawtypes")
    private List wcwtGtlcore$applyGtlcoreOutputBeforeEmptyCheck(List outputs) {
        return GtlcoreTransferBridge.applyOutputImport(WCWT_GTLCORE_CURRENT_RECIPE.get(), outputs);
    }

    @ModifyArg(
            method = "transferRecipe",
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
            method = "transferRecipe",
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
