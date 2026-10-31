import client from './client';

export async function listTimeSkips(campaignId) {
  const { data } = await client.get(`/campaigns/${campaignId}/timeskips`);
  return data;
}

export async function createTimeSkip(campaignId, payload) {
  const { data } = await client.post(`/campaigns/${campaignId}/timeskips`, payload);
  return data;
}

export async function closeTimeSkip(timeSkipId) {
  const { data } = await client.patch(`/timeskips/${timeSkipId}/close`);
  return data;
}

export async function setVenue(timeSkipId, venue) {
  const { data } = await client.patch(`/timeskips/${timeSkipId}/venue`, { venue });
  return data;
}

export async function setCurrentDay(timeSkipId, currentDay) {
  const { data } = await client.patch(`/timeskips/${timeSkipId}/current-day`, { currentDay });
  return data;
}

export async function deleteTimeSkip(timeSkipId) {
  await client.delete(`/timeskips/${timeSkipId}`);
}

export async function getFatiguePreview(timeSkipId) {
  const { data } = await client.get(`/timeskips/${timeSkipId}/fatigue-preview`);
  return data;
}
