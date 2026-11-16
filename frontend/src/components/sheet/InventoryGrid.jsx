import { useState } from 'react';
import Icon from '../ui/Icon';
import { ITEM_LOCATION } from '../ui/gear';
import { useWeapons } from '../ui/useCatalog';

const kg = (v) => (Math.round((v ?? 0) * 10) / 10).toString().replace('.', ',');
// Carga (capítulo de Condições): efeitos de cada estado acima da Carga
const LOAD_LABEL = { FREE: 'Livre', OVERLOADED: 'Sobrecarregado', LIMIT: 'Limite', IMPOSSIBLE: 'Impossível' };
const LOAD_HINT = {
  OVERLOADED: 'Movimentação pela metade; desvantagem em Destreza e Esquivas.',
  LIMIT: 'Só pode erguer ou arrastar (Ação Maior + teste de Força).',
  IMPOSSIBLE: 'O peso não se move.',
};
const ORDER = ['HAND', 'QUICK', 'WORN', 'PACK', 'DROPPED'];
const ALWAYS = ['HAND', 'QUICK'];
const DAMAGE = { BLUNT: 'Contundente', SLASH: 'Cortante', PIERCE: 'Perfurante', FIRE: 'Flamejante' };

const kindOf = (i) => (i.weaponKey ? ['swords', 'Arma'] : i.equipmentKey ? ['circle', 'Proteção/equipamento']
  : i.consumableKey ? ['bandage', 'Consumível'] : ['pack', 'Item']);
const locOf = (i) => i.location ?? 'PACK';
const states = (i) => [i.broken && 'quebrada', i.jammed && 'travada', i.damaged && !i.broken && 'danificada', i.cocked && 'engatilhada'].filter(Boolean);

export default function InventoryGrid({ items, money, derived }) {
  const weapons = useWeapons();
  const [sel, setSel] = useState(null);
  const current = items.find((i) => i.id === sel) ?? items[0];
  const d = derived;
  const pct = d?.carryKg > 0 ? Math.min(100, Math.round((d.carriedKg / d.carryKg) * 100)) : 0;
  const state = d?.loadState ?? 'FREE';
  const w = current?.weaponKey ? weapons[current.weaponKey] : null;
  const st = current ? states(current) : [];

  return (
    <div className="inv">
      <div className="inv-head">
        {money != null && <span className="tag" style={{ borderColor: 'var(--chip-el)', color: 'var(--chip-el)' }}>Diárias {money}</span>}
        {d && (
          <div className="inv-load">
            <div className="inv-bar" role="meter" aria-valuemin={0} aria-valuemax={d.carryKg} aria-valuenow={d.carriedKg}
              aria-label={`Carga ${kg(d.carriedKg)} de ${kg(d.carryKg)} kg`}>
              <span className={`inv-fill ${state !== 'FREE' ? 'inv-fill-over' : ''}`} style={{ width: `${pct}%` }} />
            </div>
            <span className="caption">Carga {kg(d.carriedKg)} / {kg(d.carryKg)} kg</span>
            <span className={state === 'FREE' ? 'tag' : 'tag tag-accent'}>{LOAD_LABEL[state]}</span>
            {LOAD_HINT[state] && <span className="hint">{LOAD_HINT[state]}</span>}
          </div>
        )}
      </div>
      {items.length === 0 ? <p className="text-sm">Inventário vazio.</p> : (
        <div className="inv-body">
          <div className="inv-groups">
            {ORDER.map((loc) => {
              const list = items.filter((i) => locOf(i) === loc);
              if (list.length === 0 && !ALWAYS.includes(loc)) return null;
              return (
                <section key={loc} aria-label={ITEM_LOCATION[loc]}>
                  <h4 className="ribbon">{ITEM_LOCATION[loc]}</h4>
                  <div className="inv-slots">
                    {list.length === 0 && <div className="inv-slot inv-empty">Vazio</div>}
                    {list.map((i, n) => {
                      const [icon, kind] = kindOf(i);
                      const s = states(i);
                      return (
                        <button key={i.id} type="button" aria-pressed={i.id === current.id}
                          aria-label={`${i.quantity}× ${i.name}, ${ITEM_LOCATION[loc]}, ${kg(i.weightKg)} kg${s.length ? `, ${s.join(', ')}` : ''}`}
                          className={`inv-slot ${n % 2 ? 'inv-odd' : ''}`} onClick={() => setSel(i.id)}>
                          <Icon name={icon} size={22} />
                          <span className="inv-name">{i.name}</span>
                          <span className="sr-only">{kind}</span>
                          {i.quantity > 1 && <span className="tag inv-qty">×{i.quantity}</span>}
                          {s.length > 0 && <span className="tag tag-accent inv-state">{s[0]}</span>}
                        </button>
                      );
                    })}
                  </div>
                </section>
              );
            })}
          </div>
          <aside className="inv-detail panel" aria-live="polite">
            <h3 className="inv-title">{current.name}</h3>
            <p className="caption">{kindOf(current)[1]} · {ITEM_LOCATION[locOf(current)]}</p>
            <p className="text-sm">
              Quantidade {current.quantity} · {kg(current.weightKg)} kg cada · {kg(current.weightKg * current.quantity)} kg no total
            </p>
            {st.length > 0 && <p>{st.map((s) => <span key={s} className="tag tag-accent mr-1">{s}</span>)}</p>}
            {current.twoHanded && <p className="text-sm">Empunhada com duas mãos.</p>}
            {w && (
              <p className="text-sm">
                {[w.dice && `Dado ${w.dice}`, w.damageType && (DAMAGE[w.damageType] ?? w.damageType), w.capacity && `Munição ${current.loaded}/${w.capacity}`,
                  w.rangeM && `Alcance ${w.rangeM} m`].filter(Boolean).join(' · ')}
              </p>
            )}
            {current.description && <p className="text-sm">{current.description}</p>}
          </aside>
        </div>
      )}
    </div>
  );
}
