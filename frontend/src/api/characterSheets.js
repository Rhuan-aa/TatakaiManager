import client from './client';

export async function createSheet(campaignId, body) {
  const { data } = await client.post(`/campaigns/${campaignId}/character-sheets`, body);
  return data;
}

export async function listSheets(campaignId) {
  const { data } = await client.get(`/campaigns/${campaignId}/character-sheets`);
  return data;
}

export async function getSheet(sheetId) {
  const { data } = await client.get(`/character-sheets/${sheetId}`);
  return data;
}

export async function updateSheet(sheetId, body) {
  const { data } = await client.put(`/character-sheets/${sheetId}`, body);
  return data;
}

export async function submitSheet(sheetId) {
  const { data } = await client.post(`/character-sheets/${sheetId}/submit`);
  return data;
}

export async function approveSheet(sheetId, { approved, reason }) {
  const { data } = await client.post(`/character-sheets/${sheetId}/approve`, { approved, reason });
  return data;
}

export async function updateResources(sheetId, body) {
  const { data } = await client.patch(`/character-sheets/${sheetId}/resources`, body);
  return data;
}

export async function gmAdjust(sheetId, body) {
  const { data } = await client.post(`/character-sheets/${sheetId}/gm-adjustments`, body);
  return data;
}

export async function rollDice(sheetId, { expression, purpose }) {
  const { data } = await client.post(`/character-sheets/${sheetId}/rolls`, { expression, purpose });
  return data;
}

export async function getSheetActivityLog(sheetId) {
  const { data } = await client.get(`/character-sheets/${sheetId}/activity-log`);
  return data;
}

export async function getCampaignActivityLog(campaignId, { sheetId, category, from, to } = {}) {
  const params = Object.fromEntries(
    Object.entries({ sheetId, category, from, to }).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  );
  const { data } = await client.get(`/campaigns/${campaignId}/activity-log`, { params });
  return data;
}

export async function createNpcSheet(npcId, body) {
  const { data } = await client.post(`/npcs/${npcId}/sheet`, body);
  return data;
}

export async function getNpcSheet(npcId) {
  const { data } = await client.get(`/npcs/${npcId}/sheet`);
  return data;
}

export async function getCampaignNpcSheet(campaignId, npcId) {
  const { data } = await client.get(`/campaigns/${campaignId}/npcs/${npcId}/sheet`);
  return data;
}

export async function getNpcSheetVisibility(campaignId, npcId) {
  const { data } = await client.get(`/campaigns/${campaignId}/npcs/${npcId}/sheet/visibility`);
  return data;
}

export async function setNpcSheetVisibility(campaignId, npcId, { section, visible }) {
  const { data } = await client.patch(`/campaigns/${campaignId}/npcs/${npcId}/sheet/visibility`, { section, visible });
  return data;
}
