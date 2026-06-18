package com.lhy.wcwt.gtlcore;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.lhy.wcwt.pull.WcwtIngredientPriorities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GtlcoreEncodingBridge {
    private static final Set<String> DATA_ITEM_IDS = Set.of(
            "gtceu:data_stick",
            "gtceu:data_orb",
            "gtceu:data_module"
    );
    private static volatile Set<Item> shapeItems;

    private GtlcoreEncodingBridge() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List filterTransferInputs(List inputs) {
        if (inputs.isEmpty()) {
            return inputs;
        }

        List filtered = new ArrayList(inputs.size());
        boolean changed = false;
        for (Object input : inputs) {
            if (input instanceof GenericStack stack) {
                if (shouldSkipTransferInput(stack)) {
                    changed = true;
                    continue;
                }
                GenericStack sanitized = stripDataItemTag(stack);
                filtered.add(sanitized);
                changed |= sanitized != stack;
            } else {
                filtered.add(input);
            }
        }
        return changed ? filtered : inputs;
    }

    public static GenericStack[] sanitizeProcessingInputs(GenericStack[] inputs) {
        GenericStack[] sanitized = null;
        for (int i = 0; i < inputs.length; i++) {
            GenericStack stack = stripDataItemTag(inputs[i]);
            if (stack != inputs[i]) {
                if (sanitized == null) {
                    sanitized = inputs.clone();
                }
                sanitized[i] = stack;
            }
        }
        return sanitized != null ? sanitized : inputs;
    }

    public static GenericStack chooseUniversalCircuit(
            WcwtIngredientPriorities.PriorityContext context,
            List<GenericStack> candidates) {
        if (context == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        GenericStack best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (GenericStack candidate : candidates) {
            if (candidate == null || !(candidate.what() instanceof AEItemKey itemKey)
                    || !isUniversalCircuit(itemKey.getItem())) {
                continue;
            }
            int priority = getPriority(context, candidate.what());
            if (best == null || priority > bestPriority) {
                best = candidate;
                bestPriority = priority;
            }
        }
        return best;
    }

    public static ItemStack chooseUniversalCircuit(
            WcwtIngredientPriorities.PriorityContext context,
            Ingredient ingredient) {
        if (context == null || ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack[] universalAlternatives = Arrays.stream(ingredient.getItems())
                .filter(stack -> stack != null && !stack.isEmpty() && isUniversalCircuit(stack.getItem()))
                .toArray(ItemStack[]::new);
        if (universalAlternatives.length == 0) {
            return ItemStack.EMPTY;
        }

        ItemStack best = ItemStack.EMPTY;
        int bestPriority = Integer.MIN_VALUE;
        for (ItemStack stack : universalAlternatives) {
            AEItemKey key = AEItemKey.of(stack);
            if (key == null || !context.ingredientPriorities().containsKey(key)) {
                continue;
            }
            int priority = getPriority(context, key);
            if (priority > bestPriority) {
                best = stack.copy();
                bestPriority = priority;
            }
        }
        return best.isEmpty() ? ItemStack.EMPTY : best.copy();
    }

    public static boolean shouldSkipTransferCandidate(GenericStack stack) {
        return stack != null && shouldSkipTransferInput(stack);
    }

    private static boolean shouldSkipTransferInput(GenericStack stack) {
        if (!(stack.what() instanceof AEItemKey itemKey)) {
            return false;
        }

        Item item = itemKey.getItem();
        if (getShapeItems().contains(item)) {
            return true;
        }

        ItemStack itemStack = itemKey.toStack();
        return itemStack.getTag() != null && itemStack.getTag().contains("assembly_line_research");
    }

    private static GenericStack stripDataItemTag(GenericStack stack) {
        if (stack == null || !(stack.what() instanceof AEItemKey itemKey) || !itemKey.hasTag()) {
            return stack;
        }
        if (!DATA_ITEM_IDS.contains(String.valueOf(BuiltInRegistries.ITEM.getKey(itemKey.getItem())))) {
            return stack;
        }
        return new GenericStack(AEItemKey.of(itemKey.getItem()), stack.amount());
    }

    private static boolean isUniversalCircuit(Item item) {
        return String.valueOf(BuiltInRegistries.ITEM.getKey(item)).contains("universal_circuit");
    }

    private static int getPriority(WcwtIngredientPriorities.PriorityContext context, AEKey key) {
        Integer priority = context.ingredientPriorities().get(key);
        return priority != null ? priority : Integer.MIN_VALUE;
    }

    private static Set<Item> getShapeItems() {
        Set<Item> current = shapeItems;
        if (current != null) {
            return current;
        }
        Set<Item> loaded = new HashSet<>();
        collectGtItems(loaded, "SHAPE_MOLDS");
        collectGtItems(loaded, "SHAPE_EXTRUDERS");
        shapeItems = Set.copyOf(loaded);
        return shapeItems;
    }

    private static void collectGtItems(Set<Item> items, String fieldName) {
        try {
            Class<?> gtItemsClass = Class.forName("com.gregtechceu.gtceu.common.data.GTItems");
            Field field = gtItemsClass.getField(fieldName);
            Object value = field.get(null);
            if (!(value instanceof Object[] entries)) {
                return;
            }
            for (Object entry : entries) {
                if (entry == null) {
                    continue;
                }
                Method get = entry.getClass().getMethod("get");
                Object item = get.invoke(entry);
                if (item instanceof Item actualItem) {
                    items.add(actualItem);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }
}
