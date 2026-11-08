import client from './client';

const base = (cid) => `/campaigns/${cid}/loose-moves`;

export const listLooseMoves = async (cid) => (await client.get(base(cid))).data;
export const createLooseMove = async (cid, body) => (await client.post(base(cid), body)).data;
export const setLooseVisible = async (cid, id, visible) => (await client.patch(`${base(cid)}/${id}/visibility`, { visible })).data;
export const deleteLooseMove = (cid, id) => client.delete(`${base(cid)}/${id}`);
