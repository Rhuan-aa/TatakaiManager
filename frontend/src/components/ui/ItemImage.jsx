import { useCallback, useEffect, useState } from 'react';
import { getCatalogItemImageUrl, listCatalogItemImages } from '../../api/catalogItemImages';

const PATH = { WEAPON: 'weapons', EQUIPMENT: 'equipment', CONSUMABLE: 'consumables' };

/** [kind, key] do item de inventário ligado ao catálogo, ou null. */
export const itemRef = (i) => (i.weaponKey ? ['WEAPON', i.weaponKey]
  : i.equipmentKey ? ['EQUIPMENT', i.equipmentKey]
    : i.consumableKey ? ['CONSUMABLE', i.consumableKey] : null);

// Object URLs compartilhados entre as telas; `rev` muda quando alguém envia/remove uma imagem.
// ponytail: cache sem limite, cresce no máximo até o número de itens do catálogo.
const cache = new Map();

/** Quem tem imagem: { scopeOf(kind, key) -> 'CAMPAIGN'|'GLOBAL'|null, rev, reload }. */
export function useItemImageIndex(campaignId) {
  const [index, setIndex] = useState(new Map());
  const [rev, setRev] = useState(0);
  useEffect(() => {
    let active = true;
    listCatalogItemImages(campaignId)
      .then((list) => active && setIndex(new Map(list.map((x) => [`${x.kind}:${x.key}`, x.scope ?? 'GLOBAL']))))
      .catch(() => active && setIndex(new Map()));
    return () => { active = false; };
  }, [campaignId, rev]);
  const scopeOf = useCallback((kind, key) => index.get(`${kind}:${key}`) ?? null, [index]);
  const reload = useCallback(() => setRev((r) => r + 1), []);
  return { scopeOf, rev, reload };
}

/** Imagem do item do catálogo (miniatura por padrão); sem imagem mostra `fallback`. */
export default function ItemImage({ kind, itemKey, campaignId, has, rev = 0, thumb = true, fallback = null, className = '' }) {
  const [url, setUrl] = useState(null);
  useEffect(() => {
    if (!has) { setUrl(null); return undefined; }
    let active = true;
    const id = `${campaignId ?? ''}|${kind}|${itemKey}|${thumb}|${rev}`;
    if (!cache.has(id)) cache.set(id, getCatalogItemImageUrl(PATH[kind], itemKey, { campaignId, thumb }));
    cache.get(id).then((u) => active && setUrl(u)).catch(() => { cache.delete(id); if (active) setUrl(null); });
    return () => { active = false; };
  }, [kind, itemKey, campaignId, has, rev, thumb]);
  if (!url) return fallback;
  return <img src={url} alt="" className={className} loading="lazy" />;
}
