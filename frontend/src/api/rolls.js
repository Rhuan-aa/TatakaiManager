import client from './client';
import { rollDice } from './characterSheets';

export async function getRollProfiles(sheetId) {
  const { data } = await client.get(`/character-sheets/${sheetId}/roll-profiles`);
  return data;
}

export async function rollTest(sheetId, body) {
  const { data } = await client.post(`/character-sheets/${sheetId}/rolls/test`, body);
  return data;
}

/** Rolagem livre por expressão (endpoint existente), com canal opcional. */
export const rollFree = rollDice;

export async function listCampaignRolls(campaignId, channel = 'PUBLIC', customChannelId) {
  const { data } = await client.get(`/campaigns/${campaignId}/rolls`, { params: { channel, customChannelId } });
  return data;
}

export async function gmRoll(campaignId, body) {
  const { data } = await client.post(`/campaigns/${campaignId}/rolls`, body);
  return data;
}

export async function revealRoll(campaignId, rollId) {
  const { data } = await client.post(`/campaigns/${campaignId}/rolls/${rollId}/reveal`);
  return data;
}

export async function listRollChannels(campaignId) {
  const { data } = await client.get(`/campaigns/${campaignId}/roll-channels`);
  return data;
}

export async function createRollChannel(campaignId, body) {
  const { data } = await client.post(`/campaigns/${campaignId}/roll-channels`, body);
  return data;
}

export async function updateRollChannel(campaignId, id, body) {
  const { data } = await client.put(`/campaigns/${campaignId}/roll-channels/${id}`, body);
  return data;
}

export async function deleteRollChannel(campaignId, id) {
  await client.delete(`/campaigns/${campaignId}/roll-channels/${id}`);
}
