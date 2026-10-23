import client from './client';

export async function listRules(campaignId) {
  const { data } = await client.get(`/campaigns/${campaignId}/rules`);
  return data;
}

export async function updateRule(campaignId, ruleKey, { enabled, params }) {
  const { data } = await client.put(`/campaigns/${campaignId}/rules/${ruleKey}`, { enabled, params });
  return data;
}
