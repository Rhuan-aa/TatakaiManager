import client from './client';

export async function listCampaigns() {
  const { data } = await client.get('/campaigns');
  return data;
}

export async function createCampaign({ name, description }) {
  const { data } = await client.post('/campaigns', { name, description });
  return data;
}
