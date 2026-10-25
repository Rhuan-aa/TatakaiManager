// Itens ligados aos catálogos de armas/equipamentos/consumíveis (plano §42–§44)
export const ITEM_LOCATION = { HAND: 'Em mãos', QUICK: 'Acesso Rápido', PACK: 'Mochila', WORN: 'Vestido', DROPPED: 'No chão' };

/** Situação de uma arma para mostrar ao lado do nome: munição, travada, engatilhada, quebrada/danificada. */
export function gearStatus(i, weapon) {
  const out = [];
  if (weapon?.capacity && weapon.reload !== 'FAST') out.push(`${i.loaded}/${weapon.capacity}`);
  if (i.jammed) out.push('travada');
  if (weapon?.slowCock && i.cocked) out.push('engatilhada');
  if (i.broken) out.push('quebrada');
  else if (i.damaged) out.push('danificada (−1 dado)');
  if (i.twoHanded) out.push('duas mãos');
  return out;
}
