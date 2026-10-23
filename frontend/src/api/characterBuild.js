import client from './client';

const b = (sheetId) => `/character-sheets/${sheetId}/build`;

async function call(method, url, body) {
  const { data } = await client[method](url, body);
  return data;
}

export const startBuild = (campaignId, { name }) =>
  call('post', `/campaigns/${campaignId}/character-sheets/build`, { name });
export const getBuild = (sheetId) => call('get', b(sheetId));
export const setIdentity = (sheetId, data) => call('put', `${b(sheetId)}/identity`, data);
export const rollTraits = (sheetId) => call('post', `${b(sheetId)}/traits/roll`);
export const chooseTraits = (sheetId, { traitIds }) => call('put', `${b(sheetId)}/traits/choice`, { traitIds });
export const resolveTraitSlot = (sheetId, index, data) =>
  call('post', `${b(sheetId)}/traits/slots/${index}/resolve`, data);
export const rollBloodTest = (sheetId) => call('post', `${b(sheetId)}/blood-test/roll`);
export const chooseClans = (sheetId, { clanIds }) => call('put', `${b(sheetId)}/clans`, { clanIds });
export const rollClans = (sheetId) => call('post', `${b(sheetId)}/clans/roll`);
export const rollAttributes = (sheetId) => call('post', `${b(sheetId)}/attributes/roll`);
export const assignAttributes = (sheetId, { assignment }) =>
  call('put', `${b(sheetId)}/attributes/assignment`, { assignment });
export const setFreePoints = (sheetId, { freePoints }) => call('put', `${b(sheetId)}/free-points`, { freePoints });
export const confirmStep = (sheetId, step) => call('post', `${b(sheetId)}/steps/${step}/confirm`);
export const goBack = (sheetId, step) => call('post', `${b(sheetId)}/back`, { step });
export const submitBuild = (sheetId) => call('post', `${b(sheetId)}/submit`);
export const reopenBuild = (sheetId, { reason }) => call('post', `${b(sheetId)}/reopen`, { reason });
