import client from './client';

const call = async (method, url, body) => (await client[method](url, body)).data;
const base = (sheetId) => `/character-sheets/${sheetId}`;

export const getNotebook = (sheetId) => call('get', `${base(sheetId)}/notebook`);
export const createDraft = (sheetId, body) => call('post', `${base(sheetId)}/notebook`, body);
export const updateDraft = (sheetId, id, body) => call('put', `${base(sheetId)}/notebook/${id}`, body);
export const deleteDraft = (sheetId, id) => call('delete', `${base(sheetId)}/notebook/${id}`);
export const submitDraft = (sheetId, id) => call('post', `${base(sheetId)}/notebook/${id}/submit`);
export const reviewDraft = (sheetId, id, approved, reason) =>
  call('post', `${base(sheetId)}/notebook/${id}/review`, { approved, reason });
export const startForge = (sheetId, draftId) => call('post', `${base(sheetId)}/forge`, { draftId });
export const abandonForge = (sheetId) => call('delete', `${base(sheetId)}/forge`);
