import client from './client';

export async function listCampaignNpcs(campaignId) {
  const { data } = await client.get(`/campaigns/${campaignId}/npcs`);
  return data;
}
