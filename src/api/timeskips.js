import client from './client';

export async function listTimeSkips(campaignId) {
  const { data } = await client.get(`/campaigns/${campaignId}/timeskips`);
  return data;
}

export async function createTimeSkip(campaignId, { name, totalDays }) {
  const { data } = await client.post(`/campaigns/${campaignId}/timeskips`, { name, totalDays });
  return data;
}

export async function closeTimeSkip(timeSkipId) {
  const { data } = await client.patch(`/timeskips/${timeSkipId}/close`);
  return data;
}

export async function setCurrentDay(timeSkipId, currentDay) {
  const { data } = await client.patch(`/timeskips/${timeSkipId}/current-day`, { currentDay });
  return data;
}
