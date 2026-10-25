// Fila global das animações de centro de tela: rolagens que chegam juntas (ataque + reações) e o golpe
// entram uma depois da outra.
let busyUntil = 0;

/** Reserva `ms` na fila e devolve quanto esperar até a vez desta animação. */
export function queueSlot(ms) {
  const now = Date.now();
  const start = Math.max(now, busyUntil);
  busyUntil = start + ms;
  return start - now;
}
