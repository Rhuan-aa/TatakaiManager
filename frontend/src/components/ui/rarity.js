// Raridades de traço: rótulo pt-BR, cor (token por tema em index.css) e ordem.
export const RARITY = {
  COMMON: { label: 'Comum', color: 'var(--rarity-common)', order: 0 },
  RARE: { label: 'Raro', color: 'var(--rarity-rare)', order: 1 },
  EPIC: { label: 'Épico', color: 'var(--rarity-epic)', order: 2 },
  LEGENDARY: { label: 'Lendário', color: 'var(--rarity-legendary)', order: 3 },
  DIVINE: { label: 'Divino', color: 'var(--rarity-divine)', order: 4 },
};

export const TRAIT_CATEGORIES = [
  { value: 'PHYSICAL', label: 'Físico' }, { value: 'MENTAL', label: 'Mental' }, { value: 'SPIRITUAL', label: 'Espiritual' },
  { value: 'HYBRID', label: 'Híbrido' }, { value: 'HEREDITARY', label: 'Hereditário' },
];

/** Estilo de `.tag` colorida pela raridade. */
export function rarityTagStyle(rarity) {
  const color = RARITY[rarity]?.color ?? 'var(--line)';
  return { borderColor: color, color };
}

/** Ordena traços da raridade mais baixa para a mais alta e, dentro dela, por nome. */
export function compareTraits(a, b) {
  const byRarity = (RARITY[a.rarity]?.order ?? 99) - (RARITY[b.rarity]?.order ?? 99);
  return byRarity || a.name.localeCompare(b.name, 'pt-BR');
}
