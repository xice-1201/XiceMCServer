package com.xice.xicemc.customitem;

import java.util.Collection;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public interface CustomItemService {
    void register(CustomItemDefinition definition);

    Optional<CustomItemDefinition> definition(NamespacedKey id);

    Collection<CustomItemDefinition> definitions();

    ItemStack create(NamespacedKey id, int amount);

    boolean isCustomItem(ItemStack item, NamespacedKey id);

    void allowCustomIngredientRecipe(NamespacedKey recipeKey);

    void disallowCustomIngredientRecipe(NamespacedKey recipeKey);

    void registerRecipe(Recipe recipe);

    void unregisterRecipe(NamespacedKey recipeKey);

    void discoverRecipe(Player player, NamespacedKey recipeKey);

    void discoverRecipes(Player player, Collection<NamespacedKey> recipeKeys);

    void rememberRecipeKnowledge(Player player, NamespacedKey knowledgeKey);

    boolean hasRecipeKnowledge(Player player, NamespacedKey knowledgeKey);

    void rememberAndDiscoverRecipe(Player player, NamespacedKey knowledgeKey, NamespacedKey recipeKey);
}
