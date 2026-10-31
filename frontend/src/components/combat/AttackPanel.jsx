import { useEffect, useState } from 'react';
import RollReveal from '../rolls/RollReveal';
import { getRollProfiles } from '../../api/rolls';
import { OUTCOME_LABEL, REACTION_LABEL, LATE_COUNTER_LABEL } from './combatLabels';

function LateCounterPicker({ sheetId, onPick, onClose }) {
  const [profiles, setProfiles] = useState(null);
  const [idx, setIdx] = useState(0);
  useEffect(() => {
    let active = true;
    getRollProfiles(sheetId).then((r) => active && setProfiles(r)).catch(() => active && setProfiles([]));
    return () => { active = false; };
  }, [sheetId]);
  const prof = profiles?.[idx];
  return (
    <div className="flex flex-wrap items-center gap-2 text-xs">
      {!profiles && <span className="caption">Carregando…</span>}
      {profiles?.length === 0 && <span className="caption">Nenhum perfil disponível.</span>}
      {profiles?.length > 0 && (
        <select className="field !w-auto !py-0.5 text-xs" aria-label="Perfil do contra-ataque" value={idx} onChange={(e) => setIdx(Number(e.target.value))}>
          {profiles.map((x, i) => <option key={i} value={i}>{x.name}{x.variation ? ` (${x.variation})` : ''} · {x.spCost} SP</option>)}
        </select>
      )}
      <button type="button" className="btn-primary px-3 py-1 text-xs" disabled={!prof}
        onClick={() => onPick({
          sourceType: prof.basicAttack ? 'BASIC_ATTACK' : 'TECHNIQUE',
          ...(prof.sourceId ? { sourceId: prof.sourceId } : {}),
          ...(prof.variation ? { variation: prof.variation } : {}),
          ...(prof.basicAttack ? { basicAttack: prof.basicAttack } : {}),
        })}>Contra-atacar</button>
      <button type="button" className="btn-ghost text-xs" onClick={onClose}>Fechar</button>
    </div>
  );
}

/** Encadear: escolhe outra técnica do atacante (o servidor confere o tipo e cobra +25% de SP). */
function ChainPicker({ sheetId, currentName, onPick, onClose }) {
  const [profiles, setProfiles] = useState(null);
  const [idx, setIdx] = useState(0);
  useEffect(() => {
    let active = true;
    getRollProfiles(sheetId)
      .then((r) => active && setProfiles(r.filter((x) => !x.basicAttack
        && `${x.name}${x.variation ? ` (${x.variation})` : ''}` !== currentName)))
      .catch(() => active && setProfiles([]));
    return () => { active = false; };
  }, [sheetId, currentName]);
  const prof = profiles?.[idx];
  return (
    <div className="flex flex-wrap items-center gap-2 text-xs">
      {!profiles && <span className="caption">Carregando…</span>}
      {profiles?.length === 0 && <span className="caption">Nenhuma outra técnica.</span>}
      {profiles?.length > 0 && (
        <select className="field !w-auto !py-0.5 text-xs" aria-label="Técnica encadeada" value={idx} onChange={(e) => setIdx(Number(e.target.value))}>
          {profiles.map((x, i) => <option key={i} value={i}>{x.name}{x.variation ? ` (${x.variation})` : ''} · {Math.ceil(x.spCost * 1.25)} SP</option>)}
        </select>
      )}
      <button type="button" className="btn-primary px-3 py-1 text-xs" disabled={!prof}
        onClick={() => onPick(prof.sourceId, prof.variation)}>Encadear</button>
      <button type="button" className="btn-ghost text-xs" onClick={onClose}>Fechar</button>
    </div>
  );
}

function Resolved({ a, fresh, targetSheetId, onLateCounter }) {
  const [picking, setPicking] = useState(null);

  return (
    <div className="panel space-y-2 p-3">
      <div className="text-sm"><strong>{a.attackerName}</strong> atacou com {a.sourceName}{a.region ? ` (${a.region})` : ''} · rodada {a.round}</div>
      {a.attackRoll && <RollReveal roll={a.attackRoll} fresh={fresh} />}
      {a.targets.map((t) => (
        <div key={t.id} className="space-y-1">
          <div className="text-sm">
            <strong>{t.targetName}</strong>: {OUTCOME_LABEL[t.outcome] ?? '—'}
            {t.reaction && t.reaction !== 'NONE' ? ` (${REACTION_LABEL[t.reaction]}${t.reactionTechniqueName ? ` com ${t.reactionTechniqueName}` : ''}${t.defenderName ? ` por ${t.defenderName}` : ''})` : ''}
            {t.mitigationPct > 0 && ` · mitigação ${t.mitigationPct}%`}
            {t.damage > 0 && ` · ${t.damage} de dano`}
            {t.counterDamage > 0 && ` · contra-ataque causou ${t.counterDamage}`}
          </div>
          {t.lateCounter && (
            <div className="text-sm">
              <span className="tag tag-accent">{LATE_COUNTER_LABEL[t.lateCounter] ?? t.lateCounter}</span>
              {t.lateCounterDamage > 0 && ` · ${t.lateCounterDamage} de dano`}
            </div>
          )}
          {t.canLateCounter && !t.lateCounter && (picking === t.id
            ? <LateCounterPicker sheetId={targetSheetId(t)} onClose={() => setPicking(null)}
                onPick={(body) => { onLateCounter(a.id, t.id, body); setPicking(null); }} />
            : <button type="button" className="btn-secondary px-3 py-1 text-xs" onClick={() => setPicking(t.id)}>Contra-ataque tardio</button>)}
          {t.reactionRoll && <RollReveal roll={t.reactionRoll} fresh={fresh} />}
        </div>
      ))}
    </div>
  );
}

export default function AttackPanel({ pending, resolved, canCancel, onReact, onCancel, onLateCounter, targetSheetId,
  attackerSheetId, onChain, freshIds }) {
  const [chaining, setChaining] = useState(null);
  return (
    <div className="space-y-3">
      {pending.length > 0 && <h3 className="panel-title text-lg">Ataques pendentes</h3>}
      {pending.map((a) => (
        <div key={a.id} className="panel space-y-2 p-3">
          <div className="text-sm">
            <strong>{a.attackerName}</strong> ataca com {a.sourceName}{a.region ? ` (${a.region})` : ''}
            <span className="tag ml-2">{a.reservedPa} PA · {a.reservedSp} SP</span>
          </div>
          {a.targets.map((t) => (
            <div key={t.id} className="flex flex-wrap items-center gap-2 text-sm">
              <span>{t.canRespond && !t.guardians ? 'Contra você' : t.targetName}{t.guardians ? ' (inconsciente)' : ''}:</span>
              {t.reaction
                ? <span className="tag">{t.autoResolved ? 'Sem reação (automático)' : REACTION_LABEL[t.reaction]}</span>
                : t.canRespond && t.options
                  ? t.options.map((o) => (
                    <button key={o.reaction} type="button" className="btn-secondary px-3 py-1 text-xs"
                      disabled={!o.available} title={o.reason ?? undefined} onClick={() => onReact(a.id, t.id, o.reaction)}>
                      {REACTION_LABEL[o.reaction]}{o.predictedBonus != null ? ` (${o.predictedBonus >= 0 ? '+' : ''}${o.predictedBonus})` : ''}
                    </button>)).concat((t.techniques ?? []).map((o) => (
                    <button key={`tec:${o.techniqueId}:${o.variation ?? ''}`} type="button" className="btn-secondary px-3 py-1 text-xs"
                      disabled={!o.available} title={o.reason ?? `${REACTION_LABEL[o.reaction]} · ${o.spCost} SP`}
                      onClick={() => onReact(a.id, t.id, o.reaction, null, { techniqueId: o.techniqueId, variation: o.variation })}>
                      {o.name} ({o.predictedBonus >= 0 ? '+' : ''}{o.predictedBonus})
                    </button>)))
                  : t.canRespond && t.guardians
                    ? t.guardians.map((g) => (
                      <span key={g.id} className="flex flex-wrap items-center gap-1">
                        <span className="caption">por {g.name}:</span>
                        {g.options.filter((o) => o.reaction !== 'NONE').map((o) => (
                          <button key={o.reaction} type="button" className="btn-secondary px-3 py-1 text-xs"
                            disabled={!o.available} title={o.reason ?? undefined} onClick={() => onReact(a.id, t.id, o.reaction, g.id)}>
                            {REACTION_LABEL[o.reaction]} ({o.predictedBonus >= 0 ? '+' : ''}{o.predictedBonus})
                          </button>))}
                      </span>)).concat(
                      <button key="none" type="button" className="btn-ghost text-xs" onClick={() => onReact(a.id, t.id, 'NONE')}>Ninguém reage</button>)
                    : <span className="caption">aguardando reação</span>}
            </div>
          ))}
          {canCancel(a) && <button type="button" className="btn-ghost text-xs" onClick={() => onCancel(a.id)}>Cancelar</button>}
          {a.canChain && (chaining === a.id
            ? <ChainPicker sheetId={attackerSheetId(a)} currentName={a.sourceName} onClose={() => setChaining(null)}
                onPick={(tid, v) => { onChain(a.id, tid, v); setChaining(null); }} />
            : <button type="button" className="btn-secondary px-3 py-1 text-xs" title="Aborta o golpe e usa outra técnica (+25% SP, sem PA)"
                onClick={() => setChaining(a.id)}>Encadear</button>)}
        </div>
      ))}
      {/* O último ataque fica sempre visível (as rolagens não podem nascer escondidas); os anteriores recolhem. */}
      {resolved.length > 0 && <h3 className="panel-title text-lg">Último ataque</h3>}
      {resolved.slice(0, 1).map((a) => <Resolved key={a.id} a={a} fresh={freshIds.has(a.id)} targetSheetId={targetSheetId} onLateCounter={onLateCounter} />)}
      {resolved.length > 1 && (
        <details>
          <summary className="caption cursor-pointer">Ataques anteriores ({resolved.length - 1})</summary>
          <div className="mt-2 space-y-2">{resolved.slice(1).map((a) => <Resolved key={a.id} a={a} fresh={false} targetSheetId={targetSheetId} onLateCounter={onLateCounter} />)}</div>
        </details>
      )}
    </div>
  );
}
