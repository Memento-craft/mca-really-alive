package com.mementocraft.reallyalive.body;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A book of recipes: what goes into a thing and what it makes, one recipe to a page, and nothing
 * on how to set a machine up. It is an ordinary written book, so a player reads it as text; a
 * villager opening it sees each page as a picture, the ingredients laid out on a crafting grid
 * beside what they make, with the machine that does the work for recipes no grid can make. The
 * written text is there for when villagers learn to read.
 */
public final class RecipeBook {
    private static final String TAG = "reallyalive_recipes";

    private RecipeBook() {}

    /**
     * What the first book of machinery covers, in order: the wooden basics, then Create's parts
     * for power and its first machines and what they make. Things not in the game are left out.
     */
    private static final List<String> MACHINERY = List.of(
            "minecraft:oak_planks", "minecraft:stick", "minecraft:crafting_table",
            "create:andesite_alloy", "create:shaft", "create:cogwheel", "create:large_cogwheel",
            "create:water_wheel", "create:hand_crank", "create:millstone", "create:wheat_flour",
            "create:depot", "create:mechanical_press", "create:iron_sheet", "create:belt_connector");

    /** The first book of machinery: how to make the parts of power sources and machines, never how to set them up. */
    public static ItemStack machinery(Level level) {
        List<Item> things = new ArrayList<>();
        for (String id : MACHINERY) {
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).ifPresent(things::add);
        }
        return write(level, "Machinery", things);
    }

    public static boolean isRecipeBook(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return stack.is(Items.WRITTEN_BOOK) && data != null && data.contains(TAG);
    }

    /** The recipes in a book, as the level knows them; recipes the level does not know are left out. */
    public static List<RecipeHolder<?>> recipes(ItemStack book, Level level) {
        List<RecipeHolder<?>> out = new ArrayList<>();
        CustomData data = book.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return out;
        }
        ListTag ids = data.copyTag().getList(TAG, 8);
        for (int i = 0; i < ids.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(ids.getString(i));
            if (id != null) {
                level.getRecipeManager().byKey(id).ifPresent(out::add);
            }
        }
        return out;
    }

    /**
     * Writes a book of every recipe that makes one of the given things: crafting recipes and the
     * machines' own (pressing, milling and the like), each with a page of text for players.
     */
    public static ItemStack write(Level level, String title, List<Item> things) {
        List<RecipeHolder<?>> chosen = new ArrayList<>();
        for (RecipeHolder<?> r : level.getRecipeManager().getRecipes()) {
            ItemStack result = r.value().getResultItem(level.registryAccess());
            if (!result.isEmpty() && things.contains(result.getItem()) && !r.value().getIngredients().isEmpty()) {
                chosen.add(r);
            }
        }
        // In the order asked for, and for each thing its crafting recipes before the machines' ones.
        chosen.sort(java.util.Comparator.<RecipeHolder<?>>comparingInt(r -> things.indexOf(r.value().getResultItem(level.registryAccess()).getItem()))
                .thenComparing(r -> machineFor(r) != null)
                .thenComparing(r -> r.id().toString()));
        ListTag ids = new ListTag();
        List<Filterable<Component>> pages = new ArrayList<>();
        for (RecipeHolder<?> r : chosen) {
            ids.add(StringTag.valueOf(r.id().toString()));
            pages.add(Filterable.passThrough(page(level, r)));
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), "Really Alive", 0, pages, true));
        CompoundTag tag = new CompoundTag();
        tag.put(TAG, ids);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return book;
    }

    /** A page of text: what is made, and from what, shown as the grid rows for a shaped recipe. */
    private static Component page(Level level, RecipeHolder<?> holder) {
        Recipe<?> r = holder.value();
        ItemStack result = r.getResultItem(level.registryAccess());
        MutableComponent text = Component.empty().append(result.getCount() + " ").append(result.getHoverName()).append("\n");
        Item machine = machineFor(holder);
        if (machine != null) {
            text.append("made by a ").append(new ItemStack(machine).getHoverName()).append(" from:\n");
        } else {
            text.append("crafted from:\n");
        }
        if (r instanceof ShapedRecipe shaped) {
            for (int y = 0; y < shaped.getHeight(); y++) {
                List<String> row = new ArrayList<>();
                for (int x = 0; x < shaped.getWidth(); x++) {
                    row.add(name(shaped.getIngredients().get(y * shaped.getWidth() + x)));
                }
                text.append(String.join(" | ", row)).append("\n");
            }
        } else {
            for (Ingredient i : r.getIngredients()) {
                text.append("- ").append(name(i)).append("\n");
            }
        }
        return text;
    }

    private static String name(Ingredient i) {
        ItemStack[] items = i.getItems();
        return i.isEmpty() || items.length == 0 ? "(empty)" : items[0].getHoverName().getString();
    }

    /**
     * The machine that makes a recipe no crafting grid makes, by the recipe's kind: a press for
     * pressing, a millstone for milling. Null for crafting, and for a kind of recipe it does not know.
     */
    static Item machineFor(RecipeHolder<?> holder) {
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
        if (type == null || "minecraft".equals(type.getNamespace()) && type.getPath().equals("crafting")) {
            return null;
        }
        String machine = switch (type.toString()) {
            case "create:pressing" -> "create:mechanical_press";
            case "create:milling" -> "create:millstone";
            case "create:crushing" -> "create:crushing_wheel";
            case "create:mixing" -> "create:mechanical_mixer";
            case "create:cutting" -> "create:mechanical_saw";
            case "create:deploying" -> "create:deployer";
            case "create:splashing" -> "create:encased_fan";
            case "minecraft:smelting" -> "minecraft:furnace";
            case "minecraft:blasting" -> "minecraft:blast_furnace";
            case "minecraft:smoking" -> "minecraft:smoker";
            case "minecraft:campfire_cooking" -> "minecraft:campfire";
            case "minecraft:stonecutting" -> "minecraft:stonecutter";
            default -> null;
        };
        return machine == null ? null : BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(machine)).orElse(null);
    }

    /**
     * The open book as a villager sees it: a page at a time, the ingredients on a 3x3 grid where a
     * crafting table has its grid, what they make where its result is, the machine that makes it to
     * the left, and a page corner (an arrow) to click for the next page. Nothing on it can be taken.
     */
    public static final class Menu extends AbstractContainerMenu {
        public static final int RESULT = 9;
        public static final int MACHINE = 10;
        public static final int NEXT = 11;
        private final List<RecipeHolder<?>> recipes;
        private final Level level;
        private final SimpleContainer page = new SimpleContainer(12);
        private int at;

        public Menu(int id, ItemStack book, Level level) {
            super(null, id);
            this.level = level;
            this.recipes = recipes(book, level);
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    addSlot(new Shown(page, y * 3 + x, 30 + x * 18, 17 + y * 18));
                }
            }
            addSlot(new Shown(page, RESULT, 124, 35));
            addSlot(new Shown(page, MACHINE, 8, 35));
            addSlot(new Shown(page, NEXT, 152, 62));
            show();
        }

        /** The recipe on the open page, or empty for an empty book. */
        public Optional<RecipeHolder<?>> shown() {
            return recipes.isEmpty() ? Optional.empty() : Optional.of(recipes.get(at));
        }

        private void show() {
            page.clearContent();
            page.setItem(NEXT, new ItemStack(Items.ARROW));
            if (recipes.isEmpty()) {
                return;
            }
            RecipeHolder<?> holder = recipes.get(at);
            Recipe<?> r = holder.value();
            NonNullList<Ingredient> ingredients = r.getIngredients();
            int w = r instanceof ShapedRecipe s ? s.getWidth() : Math.min(3, ingredients.size());
            for (int i = 0; i < ingredients.size() && i < 9; i++) {
                int x = i % w;
                int y = i / w;
                if (x < 3 && y < 3) {
                    ItemStack[] items = ingredients.get(i).getItems();
                    if (items.length > 0) {
                        page.setItem(y * 3 + x, items[0].copyWithCount(1));
                    }
                }
            }
            page.setItem(RESULT, r.getResultItem(level.registryAccess()).copy());
            Item machine = machineFor(holder);
            if (machine != null) {
                page.setItem(MACHINE, new ItemStack(machine));
            }
        }

        @Override
        public void clicked(int slot, int button, ClickType type, Player player) {
            if (slot == NEXT && !recipes.isEmpty()) {
                at = (at + 1) % recipes.size();
                show();
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    /** A picture on a page: seen, never taken, and nothing can be put on it. */
    private static final class Shown extends Slot {
        Shown(Container c, int index, int x, int y) {
            super(c, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
