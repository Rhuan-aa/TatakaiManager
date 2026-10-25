import { useCallback, useEffect, useState } from 'react';
import { getMartial } from '../../api/characterTechniques';
import { listCampaignNpcs } from '../../api/npcs';
import { listMilestones, recordFight } from '../../api/progression';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { CLASSIFICATIONS } from './sheetRules';

const EL = { EASY: 1, REAL: 2, LETHAL: 3, SPARRING: 1 };
const PS_BASE = { EASY: 1, REAL: 2, LETHAL: 3, SPARRING: 0 };
const CATEGORY = { PRESSURE: 'Pressão', DISADVANTAGE: 'Desvantagem' };
const MAX_MILESTONES = 4;

/** Marcos sugeridos pela ficha (sem motor de combate só dá para ler o estado atual); o Mestre confirma. */
const suggested = (sheet, milestones) => milestones
  .filter((m) => m.autoTrigger === 'CRITICAL_STATE' && sheet.criticalState).map((m) => m.name);

function Participant({ sheet, row, milestones, update, npcListId }) {
  const [arts, setArts] = useState(null);
  const [open, setOpen] = useState(false);
  const onChange = (patch) => update(sheet, patch);

  useEffect(() => {
    if (!row.on || arts) return undefined;
    let active = true;
    getMartial(sheet.id).then((m) => {
      if (!active) return;
      const list = m.arts.filter((a) => a.status === 'ACTIVE');
      setArts(list);
      if (list.length > 0) update(sheet, { artId: list[0].id });
    }).catch(() => active && setArts([]));
    return () => { active = false; };
  }, [row.on, arts, sheet, update]);

  const toggle = (name) => onChange({
    milestones: row.milestones.includes(name) ? row.milestones.filter((x) => x !== name) : [...row.milestones, name],
  });
  const ps = row.classification === 'SPARRING' ? 0 : PS_BASE[row.classification] + Math.min(row.milestones.length, MAX_MILESTONES);

  return (
    <li className="bubble flex flex-col gap-2 p-3">
      <div className="flex flex-wrap items-center gap-2">
        <label className="flex flex-1 items-center gap-2">
          <input type="checkbox" checked={row.on} onChange={(e) => onChange({ on: e.target.checked })} />
          <strong className="panel-title">{sheet.name}</strong>
          <span className="text-fine">{sheet.ownerName}</span>
          {sheet.criticalState && <span className="tag-accent tag">Crítico</span>}
        </label>
        {row.on && (
          <select className="field m-0 w-auto py-0.5 text-xs" aria-label={`Classificação de ${sheet.name}`}
            value={row.classification} onChange={(e) => onChange({ classification: e.target.value })}>
            {Object.entries(CLASSIFICATIONS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
          </select>
        )}
      </div>
      {row.on && (
        <>
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span style={{ color: 'var(--chip-el)' }}>até +{EL[row.classification]} EL</span>
            <span style={{ color: 'var(--chip-ps)' }}>
              {row.classification === 'SPARRING' ? 'sem PS' : `≈ +${ps} PS + metade do TAL`}
            </span>
            {arts?.length > 0 && (
              <select className="field m-0 w-auto py-0.5 text-xs" aria-label="Arte usada" value={row.artId ?? ''}
                onChange={(e) => onChange({ artId: e.target.value || null })}>
                <option value="">EL sem arte</option>
                {arts.map((a) => <option key={a.id} value={a.id}>EL para graduação · {a.artName}</option>)}
              </select>
            )}
            <input className="field m-0 w-44 py-0.5 text-xs" list={npcListId} maxLength={100} placeholder="Oponente (Rival)"
              aria-label={`Oponente de ${sheet.name}`} value={row.rival ?? ''} onChange={(e) => onChange({ rival: e.target.value })} />
            {row.classification !== 'SPARRING' && (
              <button type="button" className="btn-secondary px-2 text-xs" onClick={() => setOpen((o) => !o)}>
                Dar Pontos de Superação ({row.milestones.length} Marcos)
              </button>
            )}
          </div>
          {open && row.classification !== 'SPARRING' && (
            milestones.length === 0 ? (
              <p className="text-fine">Nenhum Marco no catálogo. Importe pelo Catálogo ({'{"milestones": [...]}'}).</p>
            ) : (
              <div className="flex flex-col gap-2">
                <p className="text-fine">
                  {row.milestones.length > MAX_MILESTONES
                    ? `${row.milestones.length} marcados — só ${MAX_MILESTONES} contam.`
                    : `Máx. ${MAX_MILESTONES} contam por luta.`}
                </p>
                {Object.entries(CATEGORY).map(([cat, title]) => {
                  const list = milestones.filter((m) => m.category === cat);
                  if (list.length === 0) return null;
                  return (
                    <fieldset key={cat} className="flex flex-col gap-1">
                      <legend className="text-xs uppercase">{title}</legend>
                      {list.map((m) => {
                        const blocked = m.seriousOnly && !['REAL', 'LETHAL'].includes(row.classification);
                        return (
                        <label key={m.id} className={`flex items-start gap-2 text-sm ${blocked ? 'opacity-50' : ''}`}>
                          <input type="checkbox" className="mt-1" disabled={blocked}
                            checked={row.milestones.includes(m.name) && !blocked} onChange={() => toggle(m.name)} />
                          <span>
                            <strong>{m.name}</strong>
                            {m.autoTrigger && <span className="tag ml-1">sugerido pela ficha</span>}
                            {m.seriousOnly && <span className="tag ml-1">só Real/Letal</span>}
                            {m.minOtherDisadvantage && <span className="tag ml-1">exige {m.minOtherDisadvantage} outros de Desvantagem</span>}
                            {m.grantsArtPoint && <span className="tag ml-1">+1 ponto à arte escolhida</span>}
                            {m.description && <span className="block text-xs opacity-80">{m.description}</span>}
                          </span>
                        </label>
                        );
                      })}
                    </fieldset>
                  );
                })}
              </div>
            )
          )}
        </>
      )}
    </li>
  );
}

/** Menu pós-luta do Mestre (F4b): quem lutou, classificação, Marcos (PS) e arte que recebe o EL. */
export default function FightClosePanel({ campaignId, sheets, onDone, preselected = [] }) {
  const toast = useToast();
  const [milestones, setMilestones] = useState([]);
  const [note, setNote] = useState('');
  const [rows, setRows] = useState({});
  const [saving, setSaving] = useState(false);
  const [npcs, setNpcs] = useState([]);
  const fighters = sheets.filter((s) => s.buildStatus === 'COMPLETED');

  useEffect(() => {
    let active = true;
    listMilestones(campaignId).then((m) => active && setMilestones(m)).catch(() => {});
    listCampaignNpcs(campaignId).then((n) => active && setNpcs(n)).catch(() => {});
    return () => { active = false; };
  }, [campaignId]);

  const row = (s) => rows[s.id] ?? { on: preselected.includes(s.id), classification: 'REAL', milestones: [], artId: null };
  const update = useCallback((s, patch) => setRows((r) => {
    const cur = r[s.id] ?? { on: preselected.includes(s.id), classification: 'REAL', milestones: suggested(s, milestones), artId: null };
    return { ...r, [s.id]: { ...cur, ...patch } };
  }), [milestones, preselected]);
  const chosen = fighters.filter((s) => row(s).on);

  async function submit() {
    setSaving(true);
    try {
      await recordFight(campaignId, {
        note: note.trim() || null,
        participants: chosen.map((s) => {
          const r = row(s);
          return {
            sheetId: s.id,
            classification: r.classification,
            milestones: r.classification === 'SPARRING' ? [] : r.milestones.filter((name) =>
              ['REAL', 'LETHAL'].includes(r.classification) || !milestones.find((m) => m.name === name)?.seriousOnly),
            artEl: r.artId ? { [r.artId]: EL[r.classification] } : {},
            rival: r.rival?.trim()
              ? { name: r.rival.trim(), npcId: npcs.find((n) => n.name === r.rival.trim())?.id ?? null } : null,
          };
        }),
      });
      toast('Luta registrada: EL aplicado e PS pendentes nas fichas.');
      onDone();
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="panel flex flex-col gap-3 p-4">
      <h2 className="panel-title">Fechar luta</h2>
      <p className="text-fine">
        Fácil = luta entre amigos ou que ambos querem evitar (EL 1×/dia); sparring dá EL até o teto da patente e não dá PS.
      </p>
      <label className="text-xs uppercase">
        Nota (opcional)
        <input className="field mt-1 w-full" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
      </label>
      <datalist id="fight-npcs">{npcs.map((n) => <option key={n.id} value={n.name} />)}</datalist>
      {fighters.length === 0 ? <p className="text-sm">Nenhuma ficha aprovada na campanha.</p> : (
        <ul className="flex flex-col gap-2">
          {fighters.map((s) => (
            <Participant key={s.id} sheet={s} row={row(s)} milestones={milestones} update={update} npcListId="fight-npcs" />
          ))}
        </ul>
      )}
      <div className="flex gap-2">
        <button type="button" className="btn-primary" disabled={chosen.length === 0 || saving} onClick={submit}>Registrar luta</button>
        <button type="button" className="btn-ghost" onClick={onDone}>Cancelar</button>
      </div>
    </div>
  );
}
