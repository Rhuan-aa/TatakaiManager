import { useEffect, useState } from 'react';
import { listWeapons } from '../../api/catalog';

let cache = null; // catálogo estático: carrega uma vez por sessão

/** Catálogo de armas (§42), indexado pela chave. */
export function useWeapons() {
  const [list, setList] = useState(cache ?? []);
  useEffect(() => {
    if (cache) return undefined;
    let active = true;
    listWeapons().then((d) => { cache = d; if (active) setList(d); }).catch(() => {});
    return () => { active = false; };
  }, []);
  return Object.fromEntries(list.map((w) => [w.key, w]));
}
