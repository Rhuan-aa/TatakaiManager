import { useContext, useState } from 'react';
import Icon from '../ui/Icon';
import { ITEM_LOCATION } from '../ui/gear';
import { useConsumables, useEquipment, useWeapons } from '../ui/useCatalog';
import FighterPlaceholder from './FighterPlaceholder';
import ItemImage, { itemRef, useItemImageIndex } from '../ui/ItemImage';
import { consumeItem, moveItem } from '../../api/characterSheets';
import { EditCtx, EditField } from './SheetEditMode';
import GmAdjustmentPanel from './GmAdjustmentPanel';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';

/** Consumíveis com uso pela ficha, fora de combate (§54); o Kit de Cutman pede o efeito. */
const SHEET_USE = {
  ANALGESICO: [['Tomar dose', null]], CALMANTE: [['Tomar dose', null]], POMADA: [['Aplicar dose', null]],
  TERMOGENICO: [['Tomar dose', null]], ENERGETICO: [['Tomar dose', null]],
  KIT_CUTMAN: [['Recuperar 2d6 HP', 'HP'], ['Fechar Feridas', 'WOUNDS']],
};

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
const USE = { SELF: 'Em si', ALLY: 'Em aliado', OFFENSIVE: 'Ofensivo', NONE: 'Fora de combate' };

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
// Espaços (espelha InventoryRules no backend): mãos 2; sem contêiner 2 no Acesso Rápido e 4 em Carregando;
// cada contêiner vestido (mochila, cinto, pochete…) abre os próprios espaços. Vestido e chão não têm limite.
const BASE_SLOTS = { HAND: 2, QUICK: 2, PACK: 4 };
const boxOf = (equipment, i) => (i && locOf(i) === 'WORN' && equipment[i.equipmentKey]?.container) || null;
/** Contêiner em que o item está de fato (vestido e do mesmo local); senão o espaço básico. */
function areaOf(i, items, equipment) {
  if (!i.containerId || !['QUICK', 'PACK'].includes(locOf(i))) return null;
  const box = items.find((x) => x.id === i.containerId);
  return boxOf(equipment, box)?.location === locOf(i) ? box.id : null;
}
/** Áreas do inventário, na ordem do boneco: lado esquerdo (mãos, acesso rápido) e direito (vestido, carregando). */
function inventoryAreas(items, equipment) {
  const boxes = items.filter((i) => boxOf(equipment, i));
  const make = (loc, box) => {
    const k = box ? boxOf(equipment, box) : null;
    const list = items.filter((i) => locOf(i) === loc
      && (loc === 'QUICK' || loc === 'PACK' ? areaOf(i, items, equipment) === (box?.id ?? null) : true));
    const cap = k ? k.slots : BASE_SLOTS[loc] ?? null;
    const limits = k ? [k.maxItemKg != null && `até ${kg(k.maxItemKg)} kg/item`, k.maxKg != null && `máx. ${kg(k.maxKg)} kg`].filter(Boolean) : [];
    return { key: box ? `${loc}:${box.id}` : loc, loc, box: box?.id ?? null, label: box ? box.name : ITEM_LOCATION[loc],
      cap, items: list, limits, full: cap != null && list.length >= cap };
  };
  const inLoc = (loc) => [make(loc), ...boxes.filter((b) => boxOf(equipment, b).location === loc).map((b) => make(loc, b))];
  return { sides: [[make('HAND'), ...inLoc('QUICK')], [make('WORN'), ...inLoc('PACK')]], dropped: make('DROPPED') };
}

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

function Slot({ item, on, onSelect, images, campaignId, removed }) {
  if (!item) return <li className="inv-slot inv-empty" aria-label="Espaço vazio"><span aria-hidden="true">Vazio</span></li>;
  const s = states(item);
  return (
    <li>
      <button type="button" className={`inv-slot${removed ? ' edit-changed edit-removed' : ''}`} aria-pressed={on} onClick={() => onSelect(item.id)}
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
/** Escolhe para onde levar o item; área cheia pede com qual item trocar. */
function MoveBox({ item, areas, busy, onMove }) {
  const all = [...areas.sides.flat(), areas.dropped];
  const here = all.find((a) => a.items.some((i) => i.id === item.id));
  const [to, setTo] = useState('');
  const [swap, setSwap] = useState('');
  const target = all.find((a) => a.key === to);
  const needsSwap = target?.full;
  return (
    <div className="inv-move flex flex-col gap-2">
      <label className="label">
        Mover para
        <select className="field mt-1 w-full" value={to} onChange={(e) => { setTo(e.target.value); setSwap(''); }}>
          <option value="">Escolha…</option>
          {all.filter((a) => a.key !== here?.key).map((a) => (
            <option key={a.key} value={a.key}>{a.label}{a.cap != null ? ` (${a.items.length}/${a.cap}${a.full ? ', cheio' : ''})` : ''}</option>
          ))}
        </select>
      </label>
      {needsSwap && (
        <label className="label">
          Trocar com
          <select className="field mt-1 w-full" value={swap} onChange={(e) => setSwap(e.target.value)}>
            <option value="">Escolha um item de {target.label}…</option>
            {target.items.map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
          </select>
        </label>
      )}
      <button type="button" className="btn-primary self-start" disabled={busy || !target || (needsSwap && !swap)}
        onClick={async () => { if (await onMove(target, swap || null)) { setTo(''); setSwap(''); } }}>
        {needsSwap ? 'Trocar' : 'Mover'}
      </button>
    </div>
  );
}

/** Inventário: boneco com o que está equipado, lista por tipo e ficha do item selecionado. */
export default function InventoryGrid({ items, money, derived, artUrl, campaignId, sheetId, canUse, canMove, sheet, onChange }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const images = useItemImageIndex(campaignId);
  const weapons = useWeapons();
  const equipment = useEquipment();
  const consumables = useConsumables();
  const [sel, setSel] = useState(null);
  const [kind, setKind] = useState('all');
  const ed = useContext(EditCtx);
  const editable = ed && !ed.readOnly;
  const [asking, setAsking] = useState(false);
  const removed = (id) => !!ed?.draft.removeItems.includes(id);
  const areas = inventoryAreas(items, equipment);

  async function move(target, swapWithId) {
    setBusy(true);
    try {
      onChange?.(await moveItem(sheetId, cur.id, { location: target.loc, containerId: target.box, swapWithId }));
      toast(swapWithId ? 'Itens trocados.' : `${cur.name} → ${target.label}`, 'success');
      return true;
    } catch (err) {
      toast(parseApiError(err).message, 'error');
      return false;
    } finally {
      setBusy(false);
    }
  }
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
        {money != null && (ed ? (
          <span className="inv-money">
            <EditField label="Diárias" before={money} after={ed.draft.money ?? money} onChange={ed.setMoney} readOnly={ed.readOnly} /> Diárias
          </span>
        ) : <span className="inv-money"><b>{money}</b> Diárias</span>)}
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

      {ed && (ed.draft.addItems.length > 0 || editable) && (
        <div className="inv-requests flex flex-col gap-2">
          {ed.draft.addItems.map((it, n) => (
            <div key={n} className="edit-changed flex items-center justify-between gap-2 p-2 text-sm">
              <span><strong>{it.name}</strong> ×{it.quantity} <span className="tag-accent tag">novo</span></span>
              {editable && <button type="button" className="btn-ghost" onClick={() => ed.dropItem(n)}>Desfazer</button>}
            </div>
          ))}
          {editable && (asking ? (
            <div className="well p-3">
              <GmAdjustmentPanel sheet={sheet} campaignId={campaignId} only="ADD_ITEM"
                onSubmit={async (body) => { ed.addItem(body.item); setAsking(false); return null; }} />
              <button type="button" className="btn-ghost mt-2" onClick={() => setAsking(false)}>Fechar</button>
            </div>
          ) : (
            <button type="button" className="btn-secondary self-start" onClick={() => setAsking(true)}>
              <Icon name="plus" size={16} /> Pedir item
            </button>
          ))}
        </div>
      )}

      <div className="inv-stage">
        <section className="inv-doll" aria-label="Equipado">
          <div className="inv-figure" aria-hidden="true">{artUrl ? <img src={artUrl} alt="" /> : <FighterPlaceholder className="inv-ph" />}</div>
          {areas.sides.map((side, n) => (
            <div key={n} className={`inv-side inv-side-${n}`}>
              {side.map((a) => {
                const min = a.cap ?? (a.loc === 'WORN' ? 4 : 0);
                const slots = [...a.items, ...Array(Math.max(0, min - a.items.length)).fill(null)];
                const over = a.cap != null && a.items.length > a.cap;
                return (
                  <div key={a.key}>
                    <h4 className="inv-loc">{a.label}{a.cap != null && ` ${a.items.length}/${a.cap}`}</h4>
                    {(a.limits.length > 0 || over) && (
                      <p className="hint">{[...a.limits, over && 'acima do limite: guarde algo'].filter(Boolean).join(' · ')}</p>
                    )}
                    <ul className="inv-slots">
                      {slots.map((i, x) => <Slot key={i?.id ?? x} item={i} on={i?.id === cur?.id} onSelect={setSel} images={images}
                        campaignId={campaignId} removed={i && removed(i.id)} />)}
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
                  <button type="button" className={`inv-row${removed(i.id) ? ' edit-changed edit-removed' : ''}`} aria-pressed={i.id === cur.id} onClick={() => setSel(i.id)}>
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
            {removed(cur.id) && <p><span className="tag-accent tag">remoção pedida</span></p>}
            {editable && (
              <button type="button" className="btn-secondary self-start" onClick={() => ed.toggleRemoveItem(cur.id)}>
                {removed(cur.id) ? 'Manter item' : 'Pedir remoção'}
              </button>
            )}
            {canMove && <MoveBox key={cur.id} item={cur} areas={areas} busy={busy} onMove={move} />}
            {text.map((t) => <p key={t} className="text-sm">{t}</p>)}
            {canUse && cur.quantity > 0 && SHEET_USE[cur.consumableKey ?? cur.equipmentKey] && (
              <p className="flex flex-wrap gap-2">
                {SHEET_USE[cur.consumableKey ?? cur.equipmentKey].map(([label, option]) => (
                  <button key={label} type="button" className="btn-primary" disabled={busy} onClick={async () => {
                    setBusy(true);
                    try {
                      onChange?.(await consumeItem(sheetId, cur.id, option));
                      toast(`${cur.name}: ${label.toLowerCase()}`, 'success');
                    } catch (err) {
                      toast(parseApiError(err).message, 'error');
                    } finally {
                      setBusy(false);
                    }
                  }}>{label}</button>
                ))}
              </p>
            )}
          </aside>
        )}
      </div>
    </div>
  );
}
