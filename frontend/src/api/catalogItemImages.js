import client from './client';

// kind: 'weapons' | 'equipment' | 'consumables'. campaignId opcional: com ele, usa a imagem da campanha
// (cai na global se não houver); sem ele, a global.
const base = (kind, key, campaignId) =>
  `${campaignId ? `/campaigns/${campaignId}` : ''}/catalog/${kind}/${encodeURIComponent(key)}/image`;

/** Baixa o blob autenticado e devolve um object URL. thumb=true pede a miniatura. */
export async function getCatalogItemImageUrl(kind, key, { campaignId, thumb = false } = {}) {
  const res = await client.get(base(kind, key, campaignId) + (thumb ? '/thumb' : ''), { responseType: 'blob' });
  return URL.createObjectURL(res.data);
}

export async function uploadCatalogItemImage(kind, key, file, campaignId) {
  const form = new FormData();
  form.append('file', file);
  await client.post(base(kind, key, campaignId), form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export async function deleteCatalogItemImage(kind, key, campaignId) {
  await client.delete(base(kind, key, campaignId));
}

/** [{kind: 'WEAPON'|'EQUIPMENT'|'CONSUMABLE', key, scope?}] dos itens que têm imagem. */
export async function listCatalogItemImages(campaignId) {
  const res = await client.get(`${campaignId ? `/campaigns/${campaignId}` : ''}/catalog/item-images`);
  return res.data;
}
