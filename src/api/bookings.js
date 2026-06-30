import client from './client';

export async function listBookings(campaignId, timeSkipId) {
  const { data } = await client.get(`/campaigns/${campaignId}/timeskips/${timeSkipId}/bookings`);
  return data;
}

export async function createBooking(campaignId, timeSkipId, { npcId, dayNumber, slotNumber, interactionType }) {
  const { data } = await client.post(
    `/campaigns/${campaignId}/timeskips/${timeSkipId}/bookings`,
    { npcId, dayNumber, slotNumber, interactionType }
  );
  return data;
}

export async function cancelBooking(bookingId) {
  await client.delete(`/bookings/${bookingId}`);
}
