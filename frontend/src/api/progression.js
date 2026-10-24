import client from './client';

const call = async (method, url, body) => (await client[method](url, body)).data;
const sheet = (sheetId) => `/character-sheets/${sheetId}`;

export const recordFight = (campaignId, body) => call('post', `/campaigns/${campaignId}/fights`, body);
export const getProgression = (sheetId) => call('get', `${sheet(sheetId)}/progression`);
export const requestOvercome = (sheetId, allocations) => call('post', `${sheet(sheetId)}/overcome`, { allocations });
export const reviewOvercome = (sheetId, id, approved, reason) =>
  call('post', `${sheet(sheetId)}/overcome/${id}/review`, { approved, reason });
export const chooseInstinct = (sheetId, instinctId) => call('post', `${sheet(sheetId)}/instincts`, { instinctId });
export const removeInstinct = (sheetId, id) => call('delete', `${sheet(sheetId)}/instincts/${id}`);
export const listInstincts = (campaignId) => call('get', '/catalog/instincts', { params: { campaignId } });
export const listMilestones = (campaignId) => call('get', '/catalog/milestones', { params: { campaignId } });
export const updateRival = (sheetId, id, body) => call('put', `${sheet(sheetId)}/rivals/${id}`, body);
export const removeRival = (sheetId, id) => call('delete', `${sheet(sheetId)}/rivals/${id}`);
