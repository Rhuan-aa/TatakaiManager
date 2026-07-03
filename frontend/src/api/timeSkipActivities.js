import client from './client';

export async function listTimeSkipActivities(campaignId, timeSkipId) {
  const { data } = await client.get(`/campaigns/${campaignId}/timeskips/${timeSkipId}/activities`);
  return data;
}

export async function createTimeSkipActivity(campaignId, timeSkipId, { name, description, idlePointCost }) {
  const { data } = await client.post(
    `/campaigns/${campaignId}/timeskips/${timeSkipId}/activities`,
    { name, description, idlePointCost }
  );
  return data;
}

export async function updateTimeSkipActivity(
  campaignId,
  timeSkipId,
  activityId,
  { name, description, idlePointCost }
) {
  const { data } = await client.put(
    `/campaigns/${campaignId}/timeskips/${timeSkipId}/activities/${activityId}`,
    { name, description, idlePointCost }
  );
  return data;
}

export async function deleteTimeSkipActivity(campaignId, timeSkipId, activityId) {
  await client.delete(`/campaigns/${campaignId}/timeskips/${timeSkipId}/activities/${activityId}`);
}
