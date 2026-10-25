import { useEffect, useState } from 'react';
import { getRollProfiles } from '../../api/rolls';
import { SIDED_BASICS, STRIKE_SIDE_LABEL } from './combatLabels';

const KINDS = [{ k: 'MAJOR', label: 'Ação maior', pa: 3 }, { k: 'MINOR_OFFENSIVE', label: 'Menor ofensiva', pa: 2 }];

export default function AttackBuilder({ participant: p, participants, onSubmit, onCancel }) {
  const [profiles, setProfiles] = useState(null);
  const [idx, setIdx] = useState(0);
  const [kind, setKind] = useState('MAJOR');
  const [targets, setTargets] = useState([]);
  const [region, setRegion] = useState('');
  const [penalty, setPenalty] = useState(0);
  const [side, setSide] = useState('');

  useEffect(() => {
    let active = true;
    getRollProfiles(p.sheetId).then((r) => active && setProfiles(r)).catch(() => active && setProfiles([]));
    return () => { active = false; };
  }, [p.sheetId]);

  const prof = profiles?.[idx];
  const sided = prof?.basicAttack ? SIDED_BASICS.includes(prof.basicAttack) : !!prof;
  const stealth = (t) => t.conditions.some((c) => c.key === 'FURTIVO');
  const toggle = (id) => setTargets((cur) => (cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]));

  function submit(e) {
    e.preventDefault();
    onSubmit({
      attackerId: p.id,
      sourceType: prof.basicAttack ? 'BASIC_ATTACK' : 'TECHNIQUE',
      ...(prof.sourceId ? { sourceId: prof.sourceId } : {}),
      ...(prof.variation ? { variation: prof.variation } : {}),
      ...(prof.basicAttack ? { basicAttack: prof.basicAttack } : {}),
      actionKind: kind,
      targetIds: targets,
      ...(region.trim() ? { region: region.trim() } : {}),
      regionPenalty: -Math.abs(penalty),
      ...(sided && side ? { strikeSide: side } : {}),
    });
  }

  return (
    <form className="panel space-y-3 p-3" onSubmit={submit}>
      <span className="panel-title text-base">Atacar com {p.name}</span>
      {!profiles && <p className="caption">Carregando…</p>}
      {profiles?.length === 0 && <p className="caption">Nenhum perfil de ataque disponível.</p>}
      {profiles?.length > 0 && (
        <>
          <div className="flex flex-wrap gap-3">
            <label className="text-xs">Ataque
              <select className="field" value={idx} onChange={(e) => setIdx(Number(e.target.value))}>
                {profiles.map((x, i) => (
                  <option key={i} value={i}>{x.name}{x.variation ? ` (${x.variation})` : ''} · {x.spCost} SP</option>
                ))}
              </select>
            </label>
            <label className="text-xs">Tipo de ação
              <select className="field" value={kind} onChange={(e) => setKind(e.target.value)}>
                {KINDS.map((x) => <option key={x.k} value={x.k}>{x.label} · {x.pa} PA</option>)}
              </select>
            </label>
            {sided && (
              <label className="text-xs">Lado do golpe
                <select className="field" value={side} onChange={(e) => setSide(e.target.value)}>
                  <option value="">{prof?.basicAttack ? 'Frente (padrão)' : '—'}</option>
                  {Object.entries(STRIKE_SIDE_LABEL).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                </select>
              </label>
            )}
            <label className="text-xs">Região
              <input className="field" maxLength={40} value={region} onChange={(e) => setRegion(e.target.value)} placeholder="Cabeça…" />
            </label>
            <label className="text-xs">Penalidade
              <input type="number" max={0} className="field !w-20" value={penalty}
                onChange={(e) => setPenalty(Math.min(0, Number(e.target.value)))} />
            </label>
          </div>
          {prof && <p className="caption">Acerto: {prof.hitFormula} · Dano: {prof.damageFormula}</p>}
          <fieldset className="flex flex-wrap gap-3">
            <legend className="caption">Alvos</legend>
            {participants.filter((t) => t.id !== p.id).map((t) => {
              const hidden = stealth(t);
              return (
                <label key={t.id} className="flex items-center gap-1 text-xs" title={hidden ? 'Furtivo: não pode ser alvo direto' : undefined}>
                  <input type="checkbox" disabled={hidden} checked={targets.includes(t.id)} onChange={() => toggle(t.id)} />
                  {t.name}{hidden ? ' (Furtivo)' : ''}
                </label>
              );
            })}
          </fieldset>
        </>
      )}
      <div className="flex gap-2">
        <button className="btn-primary" type="submit" disabled={!prof || targets.length === 0}>Declarar ataque</button>
        <button className="btn-ghost" type="button" onClick={onCancel}>Fechar</button>
      </div>
    </form>
  );
}
