import client from './client';

const base = (sheetId) => `/character-sheets/${sheetId}`;
const call = async (method, url, body) => (await client[method](url, body)).data;

export const getMartial = (sheetId) => call('get', `${base(sheetId)}/martial`);
export const requestArt = (sheetId, artId) => call('post', `${base(sheetId)}/arts`, { artId });
export const reviewCharacterArt = (sheetId, id, approved) => call('post', `${base(sheetId)}/arts/${id}/review`, { approved });
export const setArtGraduation = (sheetId, id, graduation) => call('patch', `${base(sheetId)}/arts/${id}/graduation`, { graduation });
export const removeCharacterArt = (sheetId, id) => call('delete', `${base(sheetId)}/arts/${id}`);
export const learnMove = (sheetId, body) => call('post', `${base(sheetId)}/techniques`, body);
export const reviewTechnique = (sheetId, id, approved) => call('post', `${base(sheetId)}/techniques/${id}/review`, { approved });
export const trainTechnique = (sheetId, id, points) => call('post', `${base(sheetId)}/techniques/${id}/training`, { points });
export const setTechniqueState = (sheetId, id, state) => call('patch', `${base(sheetId)}/techniques/${id}/state`, { state });
export const addMasteryPoints = (sheetId, id, points) => call('post', `${base(sheetId)}/techniques/${id}/mastery-points`, { points });
export const levelUp = (sheetId, id, bonus) => call('post', `${base(sheetId)}/techniques/${id}/level-up`, { bonus });
export const setMastery = (sheetId, id, mastery) => call('patch', `${base(sheetId)}/techniques/${id}/mastery`, { mastery });
export const removeTechnique = (sheetId, id) => call('delete', `${base(sheetId)}/techniques/${id}`);
