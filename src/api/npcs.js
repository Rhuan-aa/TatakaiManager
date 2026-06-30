import client from './client';

export async function listCampaignNpcs(campaignId) {
  const { data } = await client.get(`/campaigns/${campaignId}/npcs`);
  return data;
}

export async function getCampaignNpc(campaignId, npcId) {
  const { data } = await client.get(`/campaigns/${campaignId}/npcs/${npcId}`);
  return data;
}

export async function createNpc(body) {
  const { data } = await client.post('/npcs', body);
  return data;
}

export async function associateNpc(campaignId, npcId) {
  const { data } = await client.post(`/campaigns/${campaignId}/npcs/${npcId}`);
  return data;
}

export async function updateNpc(npcId, body) {
  const { data } = await client.put(`/npcs/${npcId}`, body);
  return data;
}

export async function setNpcVisibility(campaignId, npcId, visible) {
  const { data } = await client.patch(
    `/campaigns/${campaignId}/npcs/${npcId}/visibility`,
    { visible }
  );
  return data;
}
