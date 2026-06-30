import client from './client';

export async function listCampaigns() {
  const { data } = await client.get('/campaigns');
  return data;
}

export async function createCampaign({ name, description }) {
  const { data } = await client.post('/campaigns', { name, description });
  return data;
}

// Não há endpoint GET /campaigns/{id}: buscamos na lista do usuário.
export async function getCampaign(id) {
  const campaigns = await listCampaigns();
  return campaigns.find((c) => c.id === id) ?? null;
}

export async function inviteMember(campaignId, email) {
  const { data } = await client.post(`/campaigns/${campaignId}/members`, { email });
  return data;
}
