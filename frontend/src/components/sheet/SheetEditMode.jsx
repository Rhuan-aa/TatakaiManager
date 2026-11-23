import { createContext, useCallback, useEffect, useMemo, useState } from 'react';
import { cancelChanges, proposeChanges, reviewChanges } from '../../api/characterSheets';
import { listTraits } from '../../api/catalog';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { RARITY, compareTraits } from '../ui/rarity';

/**
 * Não nulo quando a ficha mostra alterações: no Modo edição (jogador mexe) ou com pedido pendente
 * (`readOnly`: Mestre revisando ou jogador esperando). Os componentes trocam o número fixo pelo campo/destaque.
 */
export const EditCtx = createContext(null);

const EMPTY = { attrs: {}, max: {}, el: null, ps: null, money: null, add: [], remove: [], addItems: [], removeItems: [] };
const noop = () => {};

function controls(draft, setDraft, readOnly) {
  if (readOnly) {
    return { draft, readOnly: true, setAttr: noop, setMax: noop, setEl: noop, setPs: noop, setMoney: noop, addTrait: noop,
      dropAdded: noop, toggleRemove: noop, addItem: noop, dropItem: noop, toggleRemoveItem: noop, reset: noop };
  }
  const toggle = (list, id) => (list.includes(id) ? list.filter((x) => x !== id) : [...list, id]);
  return {
    draft,
    readOnly: false,
    setAttr: (k, v) => setDraft((d) => ({ ...d, attrs: { ...d.attrs, [k]: v } })),
    setMax: (k, v) => setDraft((d) => ({ ...d, max: { ...d.max, [k]: v } })),
    setEl: (v) => setDraft((d) => ({ ...d, el: v })),
    setPs: (v) => setDraft((d) => ({ ...d, ps: v })),
    setMoney: (v) => setDraft((d) => ({ ...d, money: v })),
    addTrait: (t) => setDraft((d) => (d.add.some((x) => x.id === t.id) ? d : { ...d, add: [...d.add, t] })),
    dropAdded: (id) => setDraft((d) => ({ ...d, add: d.add.filter((x) => x.id !== id) })),
    toggleRemove: (id) => setDraft((d) => ({ ...d, remove: toggle(d.remove, id) })),
    addItem: (item) => setDraft((d) => ({ ...d, addItems: [...d.addItems, item] })),
    dropItem: (i) => setDraft((d) => ({ ...d, addItems: d.addItems.filter((_, j) => j !== i) })),
    toggleRemoveItem: (id) => setDraft((d) => ({ ...d, removeItems: toggle(d.removeItems, id) })),
    reset: () => setDraft(EMPTY),
  };
}

/** Rascunho editável do jogador. */
export function useEditDraft() {
  const [draft, setDraft] = useState(EMPTY);
  return useMemo(() => controls(draft, setDraft, false), [draft]);
}

/** Controle só leitura montado a partir de um pedido salvo (o que o Mestre revisa). */
export function readOnlyCtl(sheet, changes) {
  return controls(draftFrom(sheet, changes), noop, true);
}

/** Pedido salvo → rascunho, para desenhar as alterações no lugar de cada campo. */
export function draftFrom(sheet, changes) {
  const d = { ...EMPTY, attrs: {}, max: {}, add: [], remove: [], addItems: [], removeItems: [] };
  const p = sheet.progress ?? {};
  for (const c of changes) {
    if (c.action === 'SET_ATTRIBUTE') d.attrs[c.field] = c.value;
    else if (c.action === 'SET_MAX') d.max[c.field] = c.value;
    else if (c.action === 'GRANT_FIGHT_EXPERIENCE') d.el = (p.fightExperience ?? 0) + c.value;
    else if (c.action === 'ADJUST_OVERCOME_POINTS') d.ps = (p.pendingOvercomePoints ?? 0) + c.value;
    else if (c.action === 'ADJUST_MONEY') d.money = (sheet.money ?? 0) + c.value;
    else if (c.action === 'ADD_TRAIT') d.add.push({ id: c.targetId, name: c.field ?? 'Traço' });
    else if (c.action === 'REMOVE_TRAIT') d.remove.push(c.targetId);
    else if (c.action === 'ADD_ITEM') d.addItems.push(c.item);
    else if (c.action === 'REMOVE_ITEM') d.removeItems.push(c.targetId);
  }
  return d;
}

/** Diferenças entre a ficha e o rascunho, já no formato dos ajustes do Mestre. */
export function buildChanges(sheet, draft, reason) {
  const out = [];
  const push = (c) => out.push({ ...c, reason });
  for (const [k, v] of Object.entries(draft.attrs)) {
    if (v !== sheet.attributes?.[k]) push({ action: 'SET_ATTRIBUTE', field: k, value: v });
  }
  const base = { HP: sheet.resources?.hpMax, SP: sheet.resources?.spMax, KP: sheet.resources?.kpMax };
  for (const [k, v] of Object.entries(draft.max)) {
    if (v !== base[k]) push({ action: 'SET_MAX', field: k, value: v });
  }
  const elDelta = draft.el == null ? 0 : draft.el - sheet.progress.fightExperience;
  if (elDelta > 0) push({ action: 'GRANT_FIGHT_EXPERIENCE', value: elDelta });
  const psDelta = draft.ps == null ? 0 : draft.ps - sheet.progress.pendingOvercomePoints;
  if (psDelta !== 0) push({ action: 'ADJUST_OVERCOME_POINTS', value: psDelta });
  const moneyDelta = draft.money == null ? 0 : draft.money - (sheet.money ?? 0);
  if (moneyDelta !== 0) push({ action: 'ADJUST_MONEY', value: moneyDelta });
  draft.add.forEach((t) => push({ action: 'ADD_TRAIT', targetId: t.id, field: t.name }));
  draft.remove.forEach((id) => push({ action: 'REMOVE_TRAIT', targetId: id }));
  draft.addItems.forEach((item) => push({ action: 'ADD_ITEM', item }));
  draft.removeItems.forEach((id) => push({ action: 'REMOVE_ITEM', targetId: id }));
  return out;
}

/** Quais partes da ficha têm alteração: acende a aba, o bloco e o campo. */
export function changedParts(sheet, ctl) {
  if (!ctl) return {};
  const d = ctl.draft;
  const p = sheet.progress ?? {};
  const base = { HP: sheet.resources?.hpMax, SP: sheet.resources?.spMax, KP: sheet.resources?.kpMax };
  const attrs = Object.entries(d.attrs).some(([k, v]) => v !== sheet.attributes?.[k]);
  const resources = Object.entries(d.max).some(([k, v]) => v !== base[k])
    || (d.el != null && d.el !== p.fightExperience) || (d.ps != null && d.ps !== p.pendingOvercomePoints);
  const traits = d.add.length > 0 || d.remove.length > 0;
  const inventory = d.addItems.length > 0 || d.removeItems.length > 0 || (d.money != null && d.money !== sheet.money);
  return { attrs, resources, traits, inventory, overview: attrs || resources || traits };
}

/** Selo "alterado" para títulos de bloco. */
export function ChangedTag() {
  return <span className="tag-accent tag edit-tag">alterado</span>;
}

/** Número editável (ou só destacado, em leitura); quando difere do original mostra o valor anterior. */
export function EditField({ before, after, onChange, min = 0, label, className = '', readOnly }) {
  const changed = after !== before;
  return (
    <span className={`inline-flex flex-col ${changed ? 'edit-changed' : ''}`}>
      {readOnly ? <strong className={className}>{after}</strong> : (
        <input type="number" min={min} aria-label={label} className={`field m-0 w-20 px-1 py-0 ${className}`}
          value={after} onChange={(e) => onChange(e.target.value === '' ? before : Number(e.target.value))} />
      )}
      {changed && <span className="text-[10px] uppercase opacity-80">antes {before}</span>}
    </span>
  );
}

/** Traços pedidos (e, no Modo edição, escolha do catálogo). */
export function TraitAdder({ ctl, campaignId }) {
  const toast = useToast();
  const [catalog, setCatalog] = useState([]);
  useEffect(() => {
    if (ctl.readOnly) return undefined;
    let active = true;
    listTraits(campaignId).then((d) => active && setCatalog(d)).catch((e) => toast(parseApiError(e).message, 'error'));
    return () => { active = false; };
  }, [campaignId, toast, ctl.readOnly]);
  return (
    <div className="flex flex-col gap-2">
      {!ctl.readOnly && (
        <select className="field w-full" value="" aria-label="Adicionar traço"
          onChange={(e) => { const t = catalog.find((c) => c.id === e.target.value); if (t) ctl.addTrait({ id: t.id, name: t.name }); }}>
          <option value="">+ Adicionar traço…</option>
          {Object.entries(RARITY).map(([k, r]) => {
            const list = catalog.filter((t) => t.rarity === k).sort(compareTraits);
            return list.length > 0 && (
              <optgroup key={k} label={r.label}>{list.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}</optgroup>
            );
          })}
        </select>
      )}
      {ctl.draft.add.map((t) => (
        <div key={t.id} className="edit-changed flex items-center justify-between gap-2 p-2 text-sm">
          <span><strong>{t.name}</strong> <span className="tag-accent tag">novo</span></span>
          {!ctl.readOnly && <button type="button" className="btn-ghost" onClick={() => ctl.dropAdded(t.id)}>Desfazer</button>}
        </div>
      ))}
    </div>
  );
}

function SummaryList({ lines }) {
  return (
    <ul className="w-full list-disc pl-5 text-sm">
      {lines.map((l, i) => <li key={i}>{l}</li>)}
    </ul>
  );
}

/** Faixa do topo no Modo edição (jogador): liga o modo, resume, envia ou descarta. */
export function EditBar({ sheet, ctl, on, setOn, onSent }) {
  const toast = useToast();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const changes = buildChanges(sheet, ctl.draft, reason.trim() || '—');
  const stop = useCallback(() => { ctl.reset(); setReason(''); setOn(false); }, [ctl, setOn]);

  async function send() {
    setBusy(true);
    try {
      await proposeChanges(sheet.id, changes);
      toast('Pedido enviado ao Mestre. Nada muda até ele aprovar.');
      stop();
      onSent?.();
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }

  if (!on) {
    return <button type="button" className="btn-secondary no-print" onClick={() => setOn(true)}>Modo edição</button>;
  }
  return (
    <div className="panel edit-bar flex w-full flex-wrap items-end gap-2 p-3">
      <p className="w-full text-sm">
        <strong>Modo edição:</strong> mexa nos números, traços, itens e dinheiro direto na ficha. O que mudar fica
        destacado e só vale se o Mestre aprovar — se recusar, nada é aplicado.
      </p>
      <label className="label min-w-60 flex-1">
        Motivo do pedido (obrigatório)
        <input className="field mt-1 w-full" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <span className="tag-accent tag">{changes.length} alteração(ões)</span>
      <button type="button" className="btn-primary" disabled={busy || !reason.trim() || changes.length === 0} onClick={send}>
        Enviar para aprovação
      </button>
      <button type="button" className="btn-secondary" disabled={busy} onClick={stop}>Descartar</button>
    </div>
  );
}

/** Faixa do topo com pedido pendente: o Mestre aprova/recusa; o dono vê o que pediu e pode cancelar. */
export function PendingBar({ sheet, request, isMaster, onDone }) {
  const toast = useToast();
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const n = request.summary.length;

  async function run(fn, ok) {
    setBusy(true);
    try {
      await fn();
      toast(ok);
      setNote('');
      onDone?.();
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div id="sheet-changes" className="panel edit-bar flex w-full flex-wrap items-end gap-2 p-3" role="region"
      aria-label="Pedido de alteração">
      <p className="w-full text-sm">
        <strong>{isMaster ? `${sheet.ownerName} pediu ${n} ${n === 1 ? 'alteração' : 'alterações'}` : 'Seu pedido aguarda o Mestre'}</strong>
        {' '}— destacadas em vermelho na ficha.{request.changes[0]?.reason ? ` Motivo: ${request.changes[0].reason}` : ''}
      </p>
      <SummaryList lines={request.summary} />
      {isMaster ? (
        <>
          <label className="label min-w-60 flex-1">
            Observação (opcional)
            <input className="field mt-1 w-full" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
          </label>
          <button type="button" className="btn-primary" disabled={busy}
            onClick={() => run(() => reviewChanges(sheet.id, request.id, { approved: true, note: note.trim() || null }),
              `Aprovado: ${n} ${n === 1 ? 'alteração aplicada' : 'alterações aplicadas'}.`)}>
            Aprovar e aplicar tudo
          </button>
          <button type="button" className="btn-secondary" disabled={busy}
            onClick={() => run(() => reviewChanges(sheet.id, request.id, { approved: false, note: note.trim() || null }),
              'Pedido recusado: nada foi aplicado.')}>
            Recusar
          </button>
        </>
      ) : (
        <button type="button" className="btn-secondary" disabled={busy}
          onClick={() => run(() => cancelChanges(sheet.id, request.id), 'Pedido cancelado.')}>Cancelar pedido</button>
      )}
    </div>
  );
}
