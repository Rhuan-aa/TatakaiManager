import { useEffect, useState } from 'react';
import { rollBloodTest, chooseClans, rollClans } from '../../api/characterBuild';
import { listClans } from '../../api/catalog';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { DiceResult, DiceSequence, StepActions } from './buildUi';

export default function BloodTestStep({ build, campaignId, canAct, busy, act, confirm, burst, onBack }) {
  const toast = useToast();
  const [catalog, setCatalog] = useState([]);
  const [picked, setPicked] = useState([]);
  const [anim, setAnim] = useState(null); // beats em revelação
  const { clanSlots, bloodTestRoll, settings } = build;
  const clans = build.clans ?? [];
  const rolled = bloodTestRoll != null;
  const needsClans = rolled && clanSlots > 0;

  useEffect(() => {
    if (!canAct || !needsClans || settings.randomClan) return undefined;
    let active = true;
    listClans(campaignId)
      .then((r) => { if (active) setCatalog(r); })
      .catch((e) => { if (active) toast(parseApiError(e).message, 'error'); });
    return () => { active = false; };
  }, [campaignId, canAct, needsClans, settings.randomClan, toast]);

  const toggle = (id) => setPicked((p) => (p.includes(id) ? p.filter((x) => x !== id) : p.length < clanSlots ? [...p, id] : p));
  const hasClans = clans.length === clanSlots;
  const ready = !anim && rolled && (clanSlots === 0 || hasClans || (!settings.randomClan && picked.length === clanSlots));

  return (
    <div className="flex flex-col gap-4">
      {canAct && !rolled && (
        <div><button type="button" className="btn-primary" disabled={busy}
          onClick={() => act(() => rollBloodTest(build.sheetId)).then((r) => r && setAnim([{
            label: 'Teste de Sangue', sides: [20], values: [r.bloodTestRoll],
            caption: r.clanSlots === 0 ? 'Sem clã' : `${r.clanSlots} clã${r.clanSlots > 1 ? 's' : ''}`,
          }]))}>Rolar Teste de Sangue</button></div>
      )}
      {anim && <DiceSequence beats={anim} onDone={() => setAnim(null)} />}
      {rolled && !anim && (
        <>
          <DiceResult label="Teste de Sangue (d20)" dice={[bloodTestRoll]} burst={burst} />
          <p className="font-bold">{clanSlots === 0 ? 'Sem clã.' : `${clanSlots} clã${clanSlots > 1 ? 's' : ''} a definir.`}</p>
        </>
      )}
      {!anim && clans.length > 0 && (
        <ul className="grid gap-3 sm:grid-cols-2" aria-label="Clãs">
          {clans.map((c) => (
            <li key={c.id} className="panel p-3"><p className="panel-title text-xl">{c.name}</p>
              {c.description && <p className="text-sm">{c.description}</p>}</li>
          ))}
        </ul>
      )}
      {canAct && needsClans && !hasClans && (settings.randomClan ? (
        <div><button type="button" className="btn-primary" disabled={busy}
          onClick={() => act(() => rollClans(build.sheetId)).then((r) => r && setAnim(r.clans.map((c, i) => ({
            label: `Clã ${i + 1}`, sides: [20], values: [r.clanRolls?.[i] ?? 1], caption: c.name,
          }))))}>Rolar clãs</button></div>
      ) : (
        <fieldset className="flex flex-col gap-2">
          <legend className="label">Escolha {clanSlots} clã{clanSlots > 1 ? 's' : ''} ({picked.length}/{clanSlots})</legend>
          {catalog.map((c) => (
            <label key={c.id} className="flex items-center gap-2">
              <input type="checkbox" checked={picked.includes(c.id)} onChange={() => toggle(c.id)} />
              <span>{c.name}</span>
            </label>
          ))}
        </fieldset>
      ))}
      <StepActions canAct={canAct} busy={busy} disabled={!ready} onBack={onBack}
        onConfirm={() => confirm(!hasClans && clanSlots > 0 && !settings.randomClan
          ? () => chooseClans(build.sheetId, { clanIds: picked }) : null)} />
    </div>
  );
}
