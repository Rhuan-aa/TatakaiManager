import client from './client';

export async function listBookings(campaignId, timeSkipId) {
  const { data } = await client.get(`/campaigns/${campaignId}/timeskips/${timeSkipId}/bookings`);
  return data;
}

export async function createBooking(
  campaignId,
  timeSkipId,
  {
    npcId = null,
    dayNumber,
    slotNumber,
    interactionName = null,
    soloActivityType = null,
    activityId = null,
    description = null,
  }
) {
  const { data } = await client.post(
    `/campaigns/${campaignId}/timeskips/${timeSkipId}/bookings`,
    { npcId, dayNumber, slotNumber, interactionName, soloActivityType, activityId, description }
  );
  return data;
}

export async function cancelBooking(bookingId) {
  await client.delete(`/bookings/${bookingId}`);
}
