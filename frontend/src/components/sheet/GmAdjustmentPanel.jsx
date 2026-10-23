import { useEffect, useState } from 'react';
import { listTraits } from '../../api/catalog';
import { gmAdjust } from '../../api/characterSheets';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { ATTRS } from './sheetRules';
import { RARITY, compareTraits } from '../ui/rarity';

const RESOURCES = ['HP', 'SP', 'KP'];
const FATIGUES = [['PHYSICAL', 'Física'], ['MENTAL', 'Mental']];
const SEVERITIES = [['MINOR', 'Leve'], ['MODERATE', 'Moderado'], ['SEVERE', 'Grave'], ['LETHAL', 'Letal']];
const INJURY_TYPES = [['CUTTING', 'Corte'], ['PIERCING', 'Perfuração'], ['BLUNT', 'Contusão']];

// kind: number (value), select (field), injury, item, target (targetId)
const ACTIONS = {
  GRANT_FIGHT_EXPERIENCE: { label: 'Conceder EL', value: 'Quantidade (> 0)' },
  ADJUST_OVERCOME_POINTS: { label: 'Ajustar PS', value: 'Delta' },
  SET_RESOURCE: { label: 'Definir recurso atual', field: RESOURCES.map((r) => [r, r]), value: 'Novo valor' },
  SET_MAX: { label: 'Definir recurso máximo', field: RESOURCES.map((r) => [r, r]), value: 'Novo máximo (≥ 1)' },
  SET_ATTRIBUTE: { label: 'Definir atributo', field: ATTRS.map((a) => [a.enumName, a.label]), value: 'Novo valor' },
  SET_OPEN_WOUNDS: { label: 'Definir Feridas abertas', value: 'Quantidade' },
  ADD_INJURY: { label: 'Adicionar ferimento', injury: true },
  TREAT_INJURY: { label: 'Tratar ferimento', target: 'ID do ferimento' },
  REMOVE_INJURY: { label: 'Remover ferimento', target: 'ID do ferimento' },
  ADJUST_FATIGUE: { label: 'Ajustar Desgaste', field: FATIGUES, value: 'Delta' },
  ADJUST_MONEY: { label: 'Ajustar dinheiro', value: 'Delta' },
  ADD_ITEM: { label: 'Adicionar item', item: true },
  UPDATE_ITEM: { label: 'Atualizar item', item: true, target: 'ID do item' },
  REMOVE_ITEM: { label: 'Remover item', target: 'ID do item' },
  ADD_TRAIT: { label: 'Adicionar traço', trait: 'catalog' },
  REMOVE_TRAIT: { label: 'Remover traço', trait: 'sheet' },
};

export default function GmAdjustmentPanel({ sheet, campaignId, onDone }) {
  const toast = useToast();
  const [action, setAction] = useState('GRANT_FIGHT_EXPERIENCE');
  const [field, setField] = useState('');
  const [value, setValue] = useState('');
  const [targetId, setTargetId] = useState('');
  const [reason, setReason] = useState('');
  const [injury, setInjury] = useState({ type: 'CUTTING', region: '', severity: 'MINOR', damage: '', affectedAttribute: '' });
  const [item, setItem] = useState({ name: '', quantity: 1, description: '' });
  const [busy, setBusy] = useState(false);

  const [catalog, setCatalog] = useState([]);
  useEffect(() => {
    if (action !== 'ADD_TRAIT' || catalog.length) return undefined;
    let active = true;
    listTraits(campaignId ?? sheet.campaignId)
      .then((d) => active && setCatalog(d))
      .catch((e) => active && toast(parseApiError(e).message, 'error'));
    return () => { active = false; };
  }, [action, campaignId, sheet.campaignId, catalog.length, toast]);

  const cfg = ACTIONS[action];
  const reset = () => { setField(''); setValue(''); setTargetId(''); };

  async function submit(e) {
    e.preventDefault();
    const body = { action, reason: reason.trim() };
    if (cfg.field) body.field = field || cfg.field[0][0];
    if (cfg.value) body.value = Number(value);
    if (cfg.trait) body.targetId = targetId;
    if (cfg.target) body.targetId = targetId.trim();
    if (cfg.injury) {
      body.injury = {
        type: injury.type,
        region: injury.region,
        severity: injury.severity,
        damage: injury.damage === '' ? null : Number(injury.damage),
        affectedAttribute: injury.affectedAttribute || null,
      };
    }
    if (cfg.item) body.item = { ...item, quantity: Number(item.quantity), description: item.description || null };
    setBusy(true);
    try {
      const updated = await gmAdjust(sheet.id, body);
      toast('Ajuste aplicado.');
      setReason('');
      reset();
      onDone?.(updated);
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }

  const Sel = ({ id, lbl, val, set, opts, blank }) => (
    <label className="text-xs uppercase">
      {lbl}
      <select id={id} className="field mt-1 w-full" value={val} onChange={(e) => set(e.target.value)}>
        {blank && <option value="">—</option>}
        {opts.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
      </select>
    </label>
  );

  return (
    <form onSubmit={submit} className="flex flex-col gap-3">
      <div className="grid gap-2 sm:grid-cols-2">
        <Sel lbl="Ação" val={action} set={(v) => { setAction(v); reset(); }}
          opts={Object.entries(ACTIONS).map(([k, a]) => [k, a.label])} />
        {cfg.field && <Sel lbl="Campo" val={field || cfg.field[0][0]} set={setField} opts={cfg.field} />}
        {cfg.value && (
          <label className="text-xs uppercase">
            {cfg.value}
            <input type="number" required className="field mt-1 w-full" value={value} onChange={(e) => setValue(e.target.value)} />
          </label>
        )}
        {cfg.trait && (
          <label className="text-xs uppercase">
            Traço
            <select required className="field mt-1 w-full" value={targetId} onChange={(e) => setTargetId(e.target.value)}>
              <option value="">—</option>
              {cfg.trait === 'sheet'
                ? (sheet.traits ?? []).map((t) => <option key={t.id} value={t.id}>{t.name}</option>)
                : Object.entries(RARITY).map(([k, r]) => {
                  const list = catalog.filter((t) => t.rarity === k).sort(compareTraits);
                  return list.length > 0 && (
                    <optgroup key={k} label={r.label}>
                      {list.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
                    </optgroup>
                  );
                })}
            </select>
          </label>
        )}
        {cfg.target && (
          <label className="text-xs uppercase">
            {cfg.target}
            <input required className="field mt-1 w-full" value={targetId} onChange={(e) => setTargetId(e.target.value)} />
          </label>
        )}
      </div>
      {cfg.injury && (
        <div className="grid gap-2 sm:grid-cols-2">
          <Sel lbl="Tipo" val={injury.type} set={(v) => setInjury({ ...injury, type: v })} opts={INJURY_TYPES} />
          <Sel lbl="Gravidade" val={injury.severity} set={(v) => setInjury({ ...injury, severity: v })} opts={SEVERITIES} />
          <label className="text-xs uppercase">
            Região
            <input required maxLength={100} className="field mt-1 w-full" value={injury.region}
              onChange={(e) => setInjury({ ...injury, region: e.target.value })} />
          </label>
          <label className="text-xs uppercase">
            Dano (só contusão)
            <input type="number" min={0} className="field mt-1 w-full" value={injury.damage}
              onChange={(e) => setInjury({ ...injury, damage: e.target.value })} />
          </label>
          <Sel lbl="Atributo afetado" blank val={injury.affectedAttribute}
            set={(v) => setInjury({ ...injury, affectedAttribute: v })} opts={ATTRS.map((a) => [a.enumName, a.label])} />
        </div>
      )}
      {cfg.item && (
        <div className="grid gap-2 sm:grid-cols-2">
          <label className="text-xs uppercase">
            Item
            <input required maxLength={100} className="field mt-1 w-full" value={item.name}
              onChange={(e) => setItem({ ...item, name: e.target.value })} />
          </label>
          <label className="text-xs uppercase">
            Quantidade
            <input type="number" min={0} required className="field mt-1 w-full" value={item.quantity}
              onChange={(e) => setItem({ ...item, quantity: e.target.value })} />
          </label>
          <label className="text-xs uppercase sm:col-span-2">
            Descrição
            <input maxLength={2000} className="field mt-1 w-full" value={item.description}
              onChange={(e) => setItem({ ...item, description: e.target.value })} />
          </label>
        </div>
      )}
      <label className="text-xs uppercase">
        Motivo (obrigatório)
        <input required maxLength={500} className="field mt-1 w-full" value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <button type="submit" className="btn-primary self-start" disabled={busy || !reason.trim()}>
        {busy ? 'Aplicando…' : 'Aplicar ajuste'}
      </button>
    </form>
  );
}
