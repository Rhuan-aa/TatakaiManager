// Fila global das animações de centro de tela: rolagens que chegam juntas (ataque + reações) e o golpe
// entram uma depois da outra.
import { useEffect, useState } from 'react';

let busyUntil = 0;

/** Reserva `ms` na fila e devolve quanto esperar até a vez desta animação. */
export function queueSlot(ms) {
  const now = Date.now();
  const start = Math.max(now, busyUntil);
  busyUntil = start + ms;
  notify();
  return start - now;
}

// Quem precisa saber se há animação em andamento (ex.: o pop-up de ações some enquanto o dado rola).
const listeners = new Set();
let timer = null;
const notify = () => {
  listeners.forEach((fn) => fn(Date.now() < busyUntil));
  clearTimeout(timer);
  if (Date.now() < busyUntil) timer = setTimeout(notify, busyUntil - Date.now() + 20);
};

export function subscribeBusy(fn) {
  listeners.add(fn);
  fn(Date.now() < busyUntil);
  return () => listeners.delete(fn);
}

export function useRevealBusy() {
  const [busy, setBusy] = useState(false);
  useEffect(() => subscribeBusy(setBusy), []);
  return busy;
}
