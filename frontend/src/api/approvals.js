import client from './client';

export async function listApprovals(campaignId) {
  const { data } = await client.get(`/campaigns/${campaignId}/approvals`);
  return data;
}
