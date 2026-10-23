import { useEffect, useState } from 'react';
import { rollTraits, chooseTraits, resolveTraitSlot } from '../../api/characterBuild';
import { listTraits } from '../../api/catalog';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { RARITY, rarityTagStyle, compareTraits } from '../ui/rarity';
import RichText from '../ui/RichText';
import { DiceResult, StepActions } from './buildUi';

function TraitInfo({ trait }) {
  if (!trait) return null;
  return (
    <div className="flex flex-col gap-1">
      {trait.description && <RichText text={trait.description} />}
      {trait.requirements && <p className="caption">Requisitos: {trait.requirements}</p>}
    </div>
  );
}

function SlotCard({ slot, traits, isMaster, busy, gmRerolls, onResolve }) {
  const [pick, setPick] = useState('');
  const [reason, setReason] = useState('');
  const pending = slot.status === 'PENDING_GM';
  const options = traits.filter((t) => t.rarity === slot.rarity).sort(compareTraits);
  const canResolve = isMaster && pending && reason.trim();
  return (
    <li className="panel flex flex-col gap-2 p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="tag">Traço {slot.index + 1}</span>
        {slot.divineBonus && <span className="tag-accent tag">Divino</span>}
        {slot.rarity && <span className="tag" style={rarityTagStyle(slot.rarity)}>{RARITY[slot.rarity]?.label}</span>}
        <span className="text-sm">d100: {slot.rarityRoll ?? '—'} · {slot.rarity === 'DIVINE' ? 'd10' : 'd20'}: {slot.tableRoll ?? '—'}</span>
      </div>
      <p className="panel-title text-xl">{pending ? 'Aguardando Mestre' : slot.traitName}</p>
      {slot.note && <p className="text-sm opacity-80">{slot.note}</p>}
      {isMaster && pending && (
        <div className="flex flex-col gap-2">
          <label className="text-xs uppercase">Motivo
            <input className="field mt-1 w-full" value={reason} maxLength={300} onChange={(e) => setReason(e.target.value)} />
          </label>
          <label className="text-xs uppercase">Traço de reposição ({RARITY[slot.rarity]?.label})
            <select className="field mt-1 w-full" value={pick} onChange={(e) => setPick(e.target.value)}>
              <option value="">Escolher…</option>
              {options.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
            </select>
          </label>
          <TraitInfo trait={options.find((t) => t.id === pick)} />
          <div className="flex flex-wrap gap-2">
            <button type="button" className="btn-primary" disabled={busy || !canResolve || !pick}
              onClick={() => onResolve(slot.index, { traitId: pick, reason: reason.trim() })}>Aplicar traço</button>
            {gmRerolls && (
              <button type="button" className="btn-secondary" disabled={busy || !canResolve}
                onClick={() => onResolve(slot.index, { reroll: true, reason: reason.trim() })}>Rerrolar</button>
            )}
          </div>
        </div>
      )}
    </li>
  );
}

export default function TraitsStep({ build, campaignId, canAct, isMaster, busy, act, confirm, burst, onBack }) {
  const toast = useToast();
  const [catalog, setCatalog] = useState([]);
  const [chosen, setChosen] = useState({ COMMON: '', RARE: '', EPIC: '' });
  const { settings } = build;
  const balanced = settings.traitMethod === 'BALANCED_CHOICE';
  const slots = build.traitSlots ?? [];
  const showCatalog = balanced ? canAct : isMaster;

  useEffect(() => {
    if (!showCatalog) return undefined;
    let active = true;
    listTraits(campaignId)
      .then((r) => { if (active) setCatalog(r); })
      .catch((e) => { if (active) toast(parseApiError(e).message, 'error'); });
    return () => { active = false; };
  }, [campaignId, showCatalog, toast]);

  const rolled = slots.length > 0;
  const pending = slots.some((s) => s.status === 'PENDING_GM');
  const ready = balanced ? slots.length >= 3 || Object.values(chosen).every(Boolean) : rolled && !pending;
  const resolve = (index, data) => act(() => resolveTraitSlot(build.sheetId, index, data), data.reroll ? 'REROLL!' : null);

  return (
    <div className="flex flex-col gap-4">
      {balanced ? (
        canAct && (
          <div className="grid gap-3 sm:grid-cols-3">
            {['COMMON', 'RARE', 'EPIC'].map((r) => (
              <label key={r} className="text-xs uppercase" style={rarityTagStyle(r)}>{RARITY[r].label}
                <select className="field mt-1 w-full" value={chosen[r]} onChange={(e) => setChosen((p) => ({ ...p, [r]: e.target.value }))}>
                  <option value="">Escolher…</option>
                  {catalog.filter((t) => t.rarity === r).sort(compareTraits).map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
                </select>
                <TraitInfo trait={catalog.find((t) => t.id === chosen[r])} />
              </label>
            ))}
          </div>
        )
      ) : (
        <>
          {build.traitCountRoll != null && <DiceResult label="Quantidade de traços" dice={[build.traitCountRoll]} burst={burst} />}
          {canAct && !rolled && (
            <div><button type="button" className="btn-primary" disabled={busy}
              onClick={() => act(() => rollTraits(build.sheetId), 'ROLL!')}>Rolar traços</button></div>
          )}
        </>
      )}
      {slots.length > 0 && (
        <ol className="grid gap-3 sm:grid-cols-2" aria-label="Traços sorteados">
          {slots.map((s) => (
            <SlotCard key={s.index} slot={s} traits={catalog} isMaster={isMaster && build.buildStatus === 'IN_BUILD'}
              busy={busy} gmRerolls={settings.gmRerolls} onResolve={resolve} />
          ))}
        </ol>
      )}
      {pending && <p className="caption">Aguardando o Mestre resolver os traços pendentes.</p>}
      <StepActions canAct={canAct} busy={busy} disabled={!ready} onBack={onBack}
        onConfirm={() => confirm(balanced && slots.length < 3
          ? () => chooseTraits(build.sheetId, { traitIds: [chosen.COMMON, chosen.RARE, chosen.EPIC] })
          : null)} />
    </div>
  );
}
