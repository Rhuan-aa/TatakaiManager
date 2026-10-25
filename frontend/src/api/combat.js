import client from './client';

const get = async (url) => (await client.get(url)).data;
const post = async (url, body) => (await client.post(url, body)).data;

export const listEncounters = (cid) => get(`/campaigns/${cid}/encounters`);
export const createEncounter = (cid, name, defaultClassification) =>
  post(`/campaigns/${cid}/encounters`, { name, ...(defaultClassification ? { defaultClassification } : {}) });
export const updateEncounter = async (eid, body) => (await client.patch(`/encounters/${eid}`, body)).data;
export const setHysteria = async (eid, pid, hysteria) =>
  (await client.patch(`/encounters/${eid}/participants/${pid}/hysteria`, { hysteria })).data;
export const adjustVitals = (eid, pid, body) => post(`/encounters/${eid}/participants/${pid}/vitals`, body);
export const getEncounter = (eid) => get(`/encounters/${eid}`);
export const getEncounterEvents = (eid) => get(`/encounters/${eid}/events`);
export const addParticipant = (eid, sheetId) => post(`/encounters/${eid}/participants`, { sheetId });
export const removeParticipant = (eid, pid) => client.delete(`/encounters/${eid}/participants/${pid}`);
export const startEncounter = (eid) => post(`/encounters/${eid}/start`);
export const endEncounter = (eid) => post(`/encounters/${eid}/end`);
export const nextTurn = (eid) => post(`/encounters/${eid}/next-turn`);
export const adjustParticipant = async (eid, pid, body) =>
  (await client.patch(`/encounters/${eid}/participants/${pid}`, body)).data;
export const rerollInitiative = (eid, pid) => post(`/encounters/${eid}/participants/${pid}/reroll-initiative`);
export const doAction = (eid, body) => post(`/encounters/${eid}/actions`, body);
export const addCondition = (eid, pid, body) => post(`/encounters/${eid}/participants/${pid}/conditions`, body);
export const removeCondition = (eid, condId) => client.delete(`/encounters/${eid}/conditions/${condId}`);
export const listConditions = () => get('/catalog/conditions');
export const declareAttack = (eid, body) => post(`/encounters/${eid}/attacks`, body);
export const listAttacks = async (eid, status) =>
  (await client.get(`/encounters/${eid}/attacks`, { params: status ? { status } : {} })).data;
// defenderId: aliado que reage no lugar de um alvo Inconsciente
export const respondReaction = (aid, tid, reaction, defenderId) =>
  post(`/attacks/${aid}/targets/${tid}/reaction`, { reaction, ...(defenderId ? { defenderId } : {}) });
export const cancelAttack = (aid) => post(`/attacks/${aid}/cancel`);
export const setCounter = async (eid, id, value) =>
  (await client.patch(`/encounters/${eid}/counters/${id}`, { value })).data;
export const lateCounter = (aid, tid, body) => post(`/attacks/${aid}/targets/${tid}/late-counter`, body);
