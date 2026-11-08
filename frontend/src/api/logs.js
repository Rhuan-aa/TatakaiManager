import client from './client';

export async function listLogs(campaignId) {
  const { data } = await client.get(`/campaigns/${campaignId}/logs`);
  return data;
}

export async function createLog(campaignId, { narrative, bookingId = null }) {
  const { data } = await client.post(`/campaigns/${campaignId}/logs`, { narrative, bookingId });
  return data;
}

export async function updateLog(campaignId, logId, { narrative }) {
  const { data } = await client.put(`/campaigns/${campaignId}/logs/${logId}`, { narrative });
  return data;
}

export async function deleteLog(campaignId, logId) {
  await client.delete(`/campaigns/${campaignId}/logs/${logId}`);
}

export async function reviewLog(campaignId, logId, { approved, note = null }) {
  const { data } = await client.post(`/campaigns/${campaignId}/logs/${logId}/review`, { approved, note });
  return data;
}
