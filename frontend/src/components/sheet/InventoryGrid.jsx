import { useState } from 'react';
import Icon from '../ui/Icon';
import { ITEM_LOCATION } from '../ui/gear';
import { useConsumables, useEquipment, useWeapons } from '../ui/useCatalog';
import FighterPlaceholder from './FighterPlaceholder';
import ItemImage, { itemRef, useItemImageIndex } from '../ui/ItemImage';

const kg = (v) => (Math.round((v ?? 0) * 10) / 10).toString().replace('.', ',');
// Carga (capítulo de Condições): efeitos de cada estado acima da Carga
const LOAD_LABEL = { FREE: 'Livre', OVERLOADED: 'Sobrecarregado', LIMIT: 'Limite', IMPOSSIBLE: 'Impossível' };
const LOAD_HINT = {
  OVERLOADED: 'Movimentação pela metade; desvantagem em Destreza e Esquivas.',
  LIMIT: 'Só pode erguer ou arrastar (Ação Maior + teste de Força).',
  IMPOSSIBLE: 'O peso não se move.',
};
const DAMAGE = { BLUNT: 'Contundente', SLASH: 'Cortante', PIERCE: 'Perfurante', FIRE: 'Flamejante' };
const REACH = { CURTO: 'Curto', MEDIO: 'Médio', LONGO: 'Longo', ARREMESSO: 'Arremesso', DISPARO: 'Disparo' };
const ARMOR = { LIGHT: 'Leve', MEDIUM: 'Média', HEAVY: 'Pesada' };
const REGION = { CABECA: 'Cabeça', TRONCO: 'Tronco', BRACOS: 'Braços', PERNAS: 'Pernas' };
const USE = { SELF: 'Em si', ALLY: 'Em aliado', OFFENSIVE: 'Ofensivo' };

const KINDS = [
  { id: 'all', label: 'Tudo', icon: 'grid' },
  { id: 'weapon', label: 'Armas', icon: 'swords', one: 'Arma' },
  { id: 'equipment', label: 'Proteções', icon: 'circle', one: 'Proteção / equipamento' },
  { id: 'consumable', label: 'Consumíveis', icon: 'bandage', one: 'Consumível' },
  { id: 'other', label: 'Outros', icon: 'pack', one: 'Item' },
];
const kindOf = (i) => KINDS.find((k) => k.id === (i.weaponKey ? 'weapon' : i.equipmentKey ? 'equipment' : i.consumableKey ? 'consumable' : 'other'));
const locOf = (i) => i.location ?? 'PACK';
const states = (i) => [i.broken && 'quebrada', i.jammed && 'travada', i.damaged && !i.broken && 'danificada', i.cocked && 'engatilhada'].filter(Boolean);
// lados do boneco: [local, mínimo de espaços desenhados (só visual, sem limite de regra)]
const SIDES = [[['HAND', 2], ['QUICK', 2]], [['WORN', 4]]];

/** Linhas [rótulo, valor] do catálogo para o item (armas, proteções, consumíveis). */
function catalogRows(i, w, e, c) {
  if (w) return [
    ['Dado', w.diceTwoHands ? `${w.dice} / ${w.diceTwoHands} (2 mãos)` : w.dice],
    ['Dano', DAMAGE[w.damageType] ?? w.damageType],
    ['Alcance', (REACH[w.reach] ?? w.reach) + (w.rangeM ? ` · ${w.rangeM} m` : '')],
    ['Classe', w.weaponClass],
    ['Munição', w.capacity && `${i.loaded} / ${w.capacity}`],
  ];
  if (e) return [
    ['Categoria', ARMOR[e.category] ?? e.category],
    ...Object.entries(e.protection ?? {}).map(([t, v]) => [`Redução ${(DAMAGE[t] ?? t).toLowerCase()}`, v]),
    ['Cobre', e.cover?.map((r) => REGION[r] ?? r).join(', ')],
  ];
  if (c) return [['Uso', USE[c.use] ?? c.use], ['Dose', c.dose && 'Sim']];
  return [];
}

/** Imagem do catálogo (se houver) ou o ícone do tipo. */
function Pic({ item, images, campaignId, size, thumb = true, className = 'inv-pic' }) {
  const ref = itemRef(item);
  const icon = <Icon name={kindOf(item).icon} size={size} />;
  if (!ref) return icon;
  return (
    <ItemImage kind={ref[0]} itemKey={ref[1]} campaignId={campaignId} has={!!images.scopeOf(...ref)} rev={images.rev}
      thumb={thumb} className={className} fallback={icon} />
  );
}

function Slot({ item, on, onSelect, images, campaignId }) {
  if (!item) return <li className="inv-slot inv-empty" aria-label="Espaço vazio"><span aria-hidden="true">Vazio</span></li>;
  const s = states(item);
  return (
    <li>
      <button type="button" className="inv-slot" aria-pressed={on} onClick={() => onSelect(item.id)}
        aria-label={`${item.name}, ${ITEM_LOCATION[locOf(item)]}${s.length ? `, ${s.join(', ')}` : ''}`}>
        <Pic item={item} images={images} campaignId={campaignId} size={26} />
        <span className="inv-slot-name">{item.name}</span>
        {item.quantity > 1 && <span className="inv-qty">×{item.quantity}</span>}
        {s.length > 0 && <span className="inv-flag" title={s.join(', ')} />}
      </button>
    </li>
  );
}

/** Inventário: boneco com o que está equipado, lista por tipo e ficha do item selecionado. */
export default function InventoryGrid({ items, money, derived, artUrl, campaignId }) {
  const images = useItemImageIndex(campaignId);
  const weapons = useWeapons();
  const equipment = useEquipment();
  const consumables = useConsumables();
  const [sel, setSel] = useState(null);
  const [kind, setKind] = useState('all');
  const d = derived;
  const pct = d?.carryKg > 0 ? Math.min(100, Math.round((d.carriedKg / d.carryKg) * 100)) : 0;
  const load = d?.loadState ?? 'FREE';
  const listed = items.filter((i) => kind === 'all' || kindOf(i).id === kind);
  const cur = items.find((i) => i.id === sel) ?? listed[0] ?? items[0];
  const curKind = KINDS.find((k) => k.id === kind);

  const w = cur?.weaponKey && weapons[cur.weaponKey];
  const e = cur?.equipmentKey && equipment[cur.equipmentKey];
  const c = cur?.consumableKey && consumables[cur.consumableKey];
  const st = cur ? states(cur) : [];
  const rows = cur ? catalogRows(cur, w, e, c).filter(([, v]) => v != null && v !== false && v !== '') : [];
  const text = cur && [cur.description, e?.text, c?.text].filter(Boolean);

  return (
    <div className="inv">
      <div className="inv-head">
        {money != null && <span className="inv-money"><b>{money}</b> Diárias</span>}
        {d && (
          <div className="inv-load">
            <span className="caption">Carga {kg(d.carriedKg)} / {kg(d.carryKg)} kg</span>
            <div className="inv-bar" role="meter" aria-valuemin={0} aria-valuemax={d.carryKg} aria-valuenow={d.carriedKg}
              aria-label={`Carga ${kg(d.carriedKg)} de ${kg(d.carryKg)} kg`}>
              <span className={`inv-fill ${load !== 'FREE' ? 'inv-fill-over' : ''}`} style={{ width: `${pct}%` }} />
            </div>
            <span className={load === 'FREE' ? 'tag' : 'tag tag-accent'}>{LOAD_LABEL[load]}</span>
            {LOAD_HINT[load] && <span className="hint">{LOAD_HINT[load]}</span>}
          </div>
        )}
      </div>

      <div className="inv-stage">
        <section className="inv-doll" aria-label="Equipado">
          <div className="inv-figure" aria-hidden="true">{artUrl ? <img src={artUrl} alt="" /> : <FighterPlaceholder className="inv-ph" />}</div>
          {SIDES.map((side, n) => (
            <div key={n} className={`inv-side inv-side-${n}`}>
              {side.map(([loc, min]) => {
                const list = items.filter((i) => locOf(i) === loc);
                const slots = [...list, ...Array(Math.max(0, min - list.length)).fill(null)];
                return (
                  <div key={loc}>
                    <h4 className="inv-loc">{ITEM_LOCATION[loc]}</h4>
                    <ul className="inv-slots">
                      {slots.map((i, x) => <Slot key={i?.id ?? x} item={i} on={i?.id === cur?.id} onSelect={setSel} images={images} campaignId={campaignId} />)}
                    </ul>
                  </div>
                );
              })}
            </div>
          ))}
        </section>

        <section className="inv-bag" aria-label="Itens">
          <div className="inv-kinds" role="group" aria-label="Filtrar por tipo">
            {KINDS.map((k) => (
              <button key={k.id} type="button" className="inv-kind" aria-pressed={k.id === kind} aria-label={k.label} title={k.label}
                onClick={() => { setKind(k.id); setSel(null); }}>
                {k.id === kind && <span className="inv-kind-label" aria-hidden="true">{k.label}</span>}
                <Icon name={k.icon} size={22} />
              </button>
            ))}
          </div>
          {listed.length === 0 ? (
            <p className="inv-list inv-none">{items.length === 0 ? 'Inventário vazio.' : `Nada em ${curKind.label.toLowerCase()}.`}</p>
          ) : (
            <ul className="inv-list">
              {listed.map((i) => (
                <li key={i.id}>
                  <button type="button" className="inv-row" aria-pressed={i.id === cur.id} onClick={() => setSel(i.id)}>
                    <Pic item={i} images={images} campaignId={campaignId} size={22} />
                    <span className="inv-row-name">{i.name}</span>
                    {i.quantity > 1 && <span className="inv-row-qty">×{i.quantity}</span>}
                    <span className="inv-row-loc">{ITEM_LOCATION[locOf(i)]}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        {cur && (
          <aside className="inv-detail" aria-live="polite">
            <h3 className="inv-title">{cur.name}</h3>
            <p className="inv-sub">{kindOf(cur).one} · {ITEM_LOCATION[locOf(cur)]}</p>
            <div className="inv-art" aria-hidden="true">
              <Pic item={cur} images={images} campaignId={campaignId} size={96} thumb={false} className="inv-art-img" />
            </div>
            <dl className="inv-stats">
              {rows.length > 0 && <dt className="inv-sec">Catálogo</dt>}
              {rows.map(([k, v]) => <div key={k} className="inv-stat"><dt>{k}</dt><dd>{v}</dd></div>)}
              <dt className="inv-sec">Carga</dt>
              <div className="inv-stat"><dt>Quantidade</dt><dd>{cur.quantity}</dd></div>
              <div className="inv-stat"><dt>Peso</dt><dd>{kg(cur.weightKg)} kg</dd></div>
              {cur.quantity > 1 && <div className="inv-stat"><dt>Peso total</dt><dd>{kg(cur.weightKg * cur.quantity)} kg</dd></div>}
              {cur.twoHanded && <div className="inv-stat"><dt>Empunhadura</dt><dd>Duas mãos</dd></div>}
            </dl>
            {st.length > 0 && <p className="flex flex-wrap gap-1">{st.map((s) => <span key={s} className="tag tag-accent">{s}</span>)}</p>}
            {text.map((t) => <p key={t} className="text-sm">{t}</p>)}
          </aside>
        )}
      </div>
    </div>
  );
}
