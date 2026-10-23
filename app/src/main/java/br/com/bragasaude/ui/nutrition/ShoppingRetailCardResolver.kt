package br.com.bragasaude.ui.nutrition

import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.GroceryRetailCardEntity

/** Resolve somente a apresentação: não converte o item da dieta em compra por embalagem. */
internal fun resolveShoppingRetailCard(
    item: GroceryListItemEntity,
    cards: List<GroceryRetailCardEntity>
): GroceryRetailCardEntity? =
    cards.firstOrNull { !item.retailCardId.isNullOrBlank() && it.id == item.retailCardId }
        ?: cards.firstOrNull { !it.linkedIngredientSlug.isNullOrBlank() && it.linkedIngredientSlug == item.foodId }
        ?: cards.firstOrNull { item.foodName.isNotBlank() && it.name.trim().equals(item.foodName.trim(), ignoreCase = true) }
