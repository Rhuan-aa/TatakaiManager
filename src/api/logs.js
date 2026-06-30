import client from './client';

export async function listLogs(campaignId) {
  const { data } = await client.get(`/campaigns/${campaignId}/logs`);
  return data;
}

export async function createLog(campaignId, { narrative, bookingId = null }) {
  const { data } = await client.post(`/campaigns/${campaignId}/logs`, { narrative, bookingId });
  return data;
}
