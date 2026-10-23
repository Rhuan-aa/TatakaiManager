import client from './client';

const base = (campaignId) => (campaignId ? `/campaigns/${campaignId}/catalog` : '/catalog');
const q = (campaignId) => (campaignId ? { params: { campaignId } } : undefined);

export async function getCatalogPermissions() {
  const { data } = await client.get('/catalog/permissions');
  return data;
}

export async function listTraits(campaignId) {
  const { data } = await client.get('/catalog/traits', q(campaignId));
  return data;
}

export async function createTrait(campaignId, body) {
  const { data } = await client.post(`${base(campaignId)}/traits`, body);
  return data;
}

export async function updateTrait(id, body) {
  const { data } = await client.put(`/catalog/traits/${id}`, body);
  return data;
}

export async function deleteTrait(id) {
  const { data } = await client.delete(`/catalog/traits/${id}`);
  return data;
}

export async function listClans(campaignId) {
  const { data } = await client.get('/catalog/clans', q(campaignId));
  return data;
}

export async function createClan(campaignId, body) {
  const { data } = await client.post(`${base(campaignId)}/clans`, body);
  return data;
}

export async function updateClan(id, body) {
  const { data } = await client.put(`/catalog/clans/${id}`, body);
  return data;
}

export async function deleteClan(id) {
  const { data } = await client.delete(`/catalog/clans/${id}`);
  return data;
}

export async function importCatalog(campaignId, body) {
  const { data } = await client.post(`${base(campaignId)}/import`, body);
  return data;
}

export async function listArts(campaignId) {
  const { data } = await client.get('/catalog/arts', q(campaignId));
  return data;
}

export async function getArt(id) {
  const { data } = await client.get(`/catalog/arts/${id}`);
  return data;
}

export async function deleteArt(id) {
  const { data } = await client.delete(`/catalog/arts/${id}`);
  return data;
}
