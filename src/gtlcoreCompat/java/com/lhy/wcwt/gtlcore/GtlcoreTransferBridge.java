package com.lhy.wcwt.gtlcore;

import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

public final class GtlcoreTransferBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_LOGS = Boolean.getBoolean("wcwt.gtlcoreCompat.debug");
    private static final String MULTIBLOCK_INFO_WRAPPER_CLASS =
            "com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper";
    private static final String[] UNWRAP_METHOD_NAMES = {
            "getRecipe", "getBackingRecipe", "getWrappedRecipe", "getDelegate", "getValue",
            "getWrapped", "unwrap", "value", "recipe", "getRecipeBase", "getDelegateRecipe", "getInner"
    };
    private static final String[] INSPECTABLE_PACKAGE_PREFIXES = {
            "com.gregtechceu.", "org.gtlcore.", "com.lowdragmc.", "mezz.jei.", "dev.emi.", "com.lhy.wcwt."
    };
    private static final int MAX_UNWRAP_DEPTH = 8;
    private static final int MAX_CONTAINER_ENTRIES = 64;

    private GtlcoreTransferBridge() {
    }

    public static void traceTransferStart(Object recipe, boolean maxTransfer, boolean doTransfer) {
        if (DEBUG_LOGS && doTransfer && shouldTraceRecipe(recipe)) {
            LOGGER.info(
                    "WCWT GTLCore Compat: WCWT transfer start; recipe={}, handlesMultiblock={}, maxTransfer={}, doTransfer={}",
                    describeWithFields(recipe), handles(recipe), maxTransfer, doTransfer);
        }
    }

    public static boolean handles(Object recipe) {
        return unwrapMultiblockInfoWrapper(recipe) != null;
    }

    public static MultiblockInputFilter createMultiblockInputFilter(Object recipe) {
        Object unwrappedRecipe = unwrapMultiblockInfoWrapper(recipe);
        if (unwrappedRecipe == null) {
            return null;
        }
        String[] filterHatches = readFilterHatches();
        if (filterHatches == null) {
            LOGGER.warn("WCWT GTLCore Compat: skipped multiblock input filter; GTLCore filterHatch config unavailable; recipe={}",
                    describeClass(unwrappedRecipe));
            return null;
        }
        return new MultiblockInputFilter(filterHatches);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List applyOutputImport(Object recipe, List outputs) {
        Object unwrappedRecipe = unwrapMultiblockInfoWrapper(recipe);
        if (unwrappedRecipe == null) {
            if (DEBUG_LOGS && (outputs == null || outputs.isEmpty()) && shouldTraceRecipe(recipe)) {
                LOGGER.info("WCWT GTLCore Compat: skipped multiblock output book; wrapper not found; recipe={}",
                        describeClass(recipe));
            }
            if (DEBUG_LOGS && shouldLogMiss(recipe, outputs)) {
                LOGGER.info("WCWT GTLCore Compat: skipped multiblock output import; wrapper not found; recipe={}, outputs={}",
                        describeWithFields(recipe), summarizeStacks(outputs));
            }
            return outputs;
        }

        List<GenericStack> imported = importMultiblockOutputBook(unwrappedRecipe);
        if (DEBUG_LOGS) {
            LOGGER.info("WCWT GTLCore Compat: applied multiblock output import; recipe={}, outputs={}, imported={}",
                    describeClass(unwrappedRecipe), summarizeStacks(outputs), summarizeStacks(imported));
        }
        if (DEBUG_LOGS && imported != null && !imported.isEmpty() && (outputs == null || outputs.isEmpty())) {
            LOGGER.info("WCWT GTLCore Compat: created multiblock output book; recipe={}, imported={}",
                    describeClass(unwrappedRecipe), summarizeStacks(imported));
        }
        return imported != null ? imported : outputs;
    }

    private static Object unwrapMultiblockInfoWrapper(Object recipe) {
        if (recipe == null) {
            return null;
        }
        if (MULTIBLOCK_INFO_WRAPPER_CLASS.equals(recipe.getClass().getName())) {
            return recipe;
        }
        if (!mayContainMultiblockInfoWrapper(recipe.getClass())) {
            return null;
        }
        return findMultiblockInfoWrapper(recipe, MAX_UNWRAP_DEPTH,
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean mayContainMultiblockInfoWrapper(Class<?> type) {
        String name = type.getName().toLowerCase();
        return name.contains("multiblock") || name.contains("multi_block") || name.contains("multipage")
                || name.startsWith("mezz.jei.") || name.startsWith("dev.emi.")
                || name.startsWith("com.lowdragmc.") || name.startsWith("org.gtlcore.");
    }

    private static Object findMultiblockInfoWrapper(Object value, int depth, Set<Object> seen) {
        if (value == null || depth < 0 || !seen.add(value)) {
            return null;
        }
        if (MULTIBLOCK_INFO_WRAPPER_CLASS.equals(value.getClass().getName())) {
            return value;
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(entry -> findMultiblockInfoWrapper(entry, depth - 1, seen)).orElse(null);
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int checked = 0;
            int length = Array.getLength(value);
            for (int i = 0; i < length && checked < MAX_CONTAINER_ENTRIES; i++, checked++) {
                Object found = findMultiblockInfoWrapper(Array.get(value, i), depth - 1, seen);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            int checked = 0;
            for (Object entry : iterable) {
                Object found = findMultiblockInfoWrapper(entry, depth - 1, seen);
                if (found != null) {
                    return found;
                }
                if (++checked >= MAX_CONTAINER_ENTRIES) {
                    break;
                }
            }
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            int checked = 0;
            for (Object entry : map.values()) {
                Object found = findMultiblockInfoWrapper(entry, depth - 1, seen);
                if (found != null) {
                    return found;
                }
                if (++checked >= MAX_CONTAINER_ENTRIES) {
                    break;
                }
            }
            return null;
        }

        if (!isInspectable(type)) {
            return null;
        }

        for (String methodName : UNWRAP_METHOD_NAMES) {
            Object found = findMultiblockInfoWrapper(invokeNoArg(value, type, methodName), depth - 1, seen);
            if (found != null) {
                return found;
            }
        }

        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                Object found = findMultiblockInfoWrapper(readField(value, field), depth - 1, seen);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean isInspectable(Class<?> type) {
        String name = type.getName();
        for (String prefix : INSPECTABLE_PACKAGE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static Object invokeNoArg(Object instance, Class<?> type, String methodName) {
        try {
            Method method = type.getMethod(methodName);
            if (!Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0) {
                method.setAccessible(true);
                return method.invoke(instance);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                    return null;
                }
                method.setAccessible(true);
                return method.invoke(instance);
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object readField(Object instance, Field field) {
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean matchesFilterHatch(GenericStack stack, String[] filterHatches) {
        String id = stack.what().getId().toString();
        for (String filterHatch : filterHatches) {
            if (filterHatch != null && !filterHatch.isEmpty() && id.contains(filterHatch)) {
                return true;
            }
        }
        return false;
    }

    private static List<GenericStack> importMultiblockOutputBook(Object multiblockInfoWrapper) {
        try {
            Object definition = readFieldByName(multiblockInfoWrapper, "definition");
            if (definition == null) {
                LOGGER.warn("WCWT GTLCore Compat: cannot create multiblock output book; definition field missing; recipe={}",
                        describeClass(multiblockInfoWrapper));
                return null;
            }
            Object id = invokeNoArg(definition, definition.getClass(), "getId");
            if (id == null) {
                LOGGER.warn("WCWT GTLCore Compat: cannot create multiblock output book; definition id missing; recipe={}",
                        describeClass(multiblockInfoWrapper));
                return null;
            }
            Object languageKey = invokeOneArg(id, id.getClass(), "toLanguageKey", String.class, "block");
            String key = languageKey instanceof String stringKey ? stringKey : fallbackBlockLanguageKey(id);
            if (key == null || key.isEmpty()) {
                return null;
            }
            Component title = Component.translatable(key).withStyle(style -> style.withColor(16536828));
            ItemStack book = nameItemStackLikeKubejs(Items.WRITTEN_BOOK.getDefaultInstance(), title);
            GenericStack stack = GenericStack.fromItemStack(book);
            return stack == null ? List.of() : List.of(stack);
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("WCWT GTLCore Compat: cannot create multiblock output book; recipe={}",
                    describeClass(multiblockInfoWrapper), error);
            return null;
        }
    }

    private static String fallbackBlockLanguageKey(Object id) {
        Object namespace = invokeNoArg(id, id.getClass(), "getNamespace");
        Object path = invokeNoArg(id, id.getClass(), "getPath");
        if (namespace instanceof String namespaceString && path instanceof String pathString) {
            return "block." + namespaceString + "." + pathString;
        }

        String raw = String.valueOf(id);
        int separator = raw.indexOf(':');
        if (separator > 0 && separator < raw.length() - 1) {
            return "block." + raw.substring(0, separator) + "." + raw.substring(separator + 1);
        }
        return null;
    }

    private static Object invokeOneArg(Object instance, Class<?> type, String methodName,
                                       Class<?> parameterType, Object argument) {
        try {
            Method method = type.getMethod(methodName, parameterType);
            if (!Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 1) {
                method.setAccessible(true);
                return method.invoke(instance, argument);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(methodName, parameterType);
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
                    return null;
                }
                method.setAccessible(true);
                return method.invoke(instance, argument);
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }
        return null;
    }

    private static ItemStack nameItemStackLikeKubejs(ItemStack stack, Component title) {
        try {
            Method method = stack.getClass().getMethod("kjs$withName", Component.class);
            method.setAccessible(true);
            Object result = method.invoke(stack, title);
            if (result instanceof ItemStack namedStack) {
                return namedStack;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        for (Class<?> current = stack.getClass(); current != null && current != Object.class;
             current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod("kjs$withName", Component.class);
                method.setAccessible(true);
                Object result = method.invoke(stack, title);
                if (result instanceof ItemStack namedStack) {
                    return namedStack;
                }
                break;
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                break;
            }
        }
        stack.setHoverName(title);
        return stack;
    }

    private static Object readFieldByName(Object instance, String fieldName) {
        for (Class<?> current = instance.getClass(); current != null && current != Object.class;
             current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(fieldName);
                return readField(instance, field);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static boolean shouldLogMiss(Object recipe, List<?> stacks) {
        if (recipe == null) {
            return false;
        }
        if (shouldTraceRecipe(recipe)) {
            return true;
        }
        return stacks == null || stacks.isEmpty();
    }

    private static boolean shouldTraceRecipe(Object recipe) {
        if (recipe == null) {
            return false;
        }
        String name = recipe.getClass().getName().toLowerCase();
        return name.contains("multiblock") || name.contains("multi_block")
                || name.contains("gregtech") || name.contains("gtceu") || name.contains("gtlcore");
    }

    private static String describeClass(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static String describeWithFields(Object value) {
        if (value == null) {
            return "null";
        }
        String fields = describeShallowFields(value);
        return fields.isEmpty() ? describeClass(value) : describeClass(value) + " fields=[" + fields + "]";
    }

    private static String describeShallowFields(Object value) {
        Class<?> type = value.getClass();
        if (!isInspectable(type)) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(", ");
        int added = 0;
        for (Class<?> current = type; current != null && current != Object.class && added < 12;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                Object fieldValue = readField(value, field);
                if (fieldValue != null) {
                    joiner.add(field.getName() + "=" + describeClass(fieldValue));
                    if (++added >= 12) {
                        break;
                    }
                }
            }
        }
        return joiner.toString();
    }

    private static String summarizeStacks(List<?> stacks) {
        if (stacks == null) {
            return "null";
        }
        StringJoiner joiner = new StringJoiner(", ", stacks.size() + " [", "]");
        int added = 0;
        for (Object entry : stacks) {
            if (added >= 16) {
                joiner.add("...");
                break;
            }
            joiner.add(summarizeStack(entry));
            added++;
        }
        return joiner.toString();
    }

    private static String summarizeStack(Object entry) {
        if (entry == null) {
            return "null";
        }
        if (entry instanceof GenericStack stack) {
            try {
                return stack.what().getId() + " x" + stack.amount();
            } catch (RuntimeException | LinkageError error) {
                return describeClass(entry);
            }
        }
        return describeClass(entry);
    }

    private static String[] readFilterHatches() {
        try {
            Class<?> holderClass = Class.forName("org.gtlcore.gtlcore.config.ConfigHolder");
            Field instanceField = holderClass.getField("INSTANCE");
            Object instance = instanceField.get(null);
            if (instance == null) {
                return null;
            }
            Field filterHatchField = holderClass.getField("filterHatch");
            Object value = filterHatchField.get(instance);
            return value instanceof String[] strings ? strings : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            LOGGER.warn("WCWT GTLCore Compat: cannot read GTLCore filterHatch config", error);
            return null;
        }
    }

    public static final class MultiblockInputFilter {
        private final String[] filterHatches;

        private MultiblockInputFilter(String[] filterHatches) {
            this.filterHatches = filterHatches;
        }

        public boolean shouldDropCandidateGroup(List<GenericStack> group) {
            if (filterHatches.length == 0 || group == null || group.isEmpty()) {
                return false;
            }
            for (GenericStack stack : group) {
                if (stack != null && !matchesFilterHatch(stack, filterHatches)) {
                    return false;
                }
            }
            return true;
        }
    }
}
