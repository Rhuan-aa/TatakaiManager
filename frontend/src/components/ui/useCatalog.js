import { useEffect, useState } from 'react';
import { listConsumables, listEquipment, listWeapons } from '../../api/catalog';

const cache = {}; // catálogos estáticos: carregam uma vez por sessão

function useCatalog(name, loader) {
  const [list, setList] = useState(cache[name] ?? []);
  useEffect(() => {
    if (cache[name]) return undefined;
    let active = true;
    loader().then((d) => { cache[name] = d; if (active) setList(d); }).catch(() => {});
    return () => { active = false; };
  }, [name, loader]);
  return Object.fromEntries(list.map((w) => [w.key, w]));
}

/** Catálogo de armas (§42), indexado pela chave. */
export const useWeapons = () => useCatalog('weapons', listWeapons);
/** Catálogo de consumíveis (§44), indexado pela chave. */
export const useConsumables = () => useCatalog('consumables', listConsumables);
/** Catálogo de proteções e equipamentos (§43), indexado pela chave. */
export const useEquipment = () => useCatalog('equipment', listEquipment);
