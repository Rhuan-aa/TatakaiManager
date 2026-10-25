import { useEffect, useState } from 'react';
import { listConditions } from '../../api/combat';
import { expiresLabel } from './combatLabels';

export default function ConditionPicker({ participant, participants, onSubmit, onCancel }) {
  const [catalog, setCatalog] = useState([]);
  const [key, setKey] = useState('');
  const [expiresAt, setExpiresAt] = useState('');
  const [turns, setTurns] = useState(2);
  const [intensity, setIntensity] = useState(1);
  const [source, setSource] = useState('');
  const [note, setNote] = useState('');

  useEffect(() => {
    let active = true;
    listConditions().then((c) => active && setCatalog(c)).catch(() => {});
    return () => { active = false; };
  }, []);

  const def = catalog.find((c) => c.key === key);
  const exp = expiresAt || def?.defaultExpiresAt || 'MANUAL';

  function submit(e) {
    e.preventDefault();
    onSubmit(participant.id, {
      key,
      ...(expiresAt ? { expiresAt } : {}),
      ...(exp === 'COUNTER' ? { turnsRemaining: Number(turns) } : {}),
      ...(def?.stack === 'SUM' ? { intensity: Number(intensity) } : {}),
      ...(source ? { sourceParticipantId: source } : {}),
      ...(note.trim() ? { note: note.trim() } : {}),
    });
  }

  return (
    <form onSubmit={submit} className="panel space-y-2 p-3">
      <div className="panel-title text-base">Condição em {participant.name}</div>
      <select className="field" required value={key} onChange={(e) => setKey(e.target.value)}>
        <option value="">Escolha…</option>
        {catalog.map((c) => <option key={c.key} value={c.key}>{c.name}</option>)}
      </select>
      {def && (
        <p className="text-xs"><b>Efeito:</b> {def.effect} <b>Saída:</b> {def.exit}</p>
      )}
      <div className="flex flex-wrap gap-2">
        <select className="field !w-auto" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)}>
          <option value="">Padrão{def ? ` (${expiresLabel({ expiresAt: def.defaultExpiresAt, turnsRemaining: turns })})` : ''}</option>
          {['START_OF_TARGET_TURN', 'END_OF_TARGET_TURN', 'END_OF_ROUND', 'COUNTER', 'MANUAL', 'END_OF_COMBAT'].map((x) => (
            <option key={x} value={x}>{expiresLabel({ expiresAt: x, turnsRemaining: turns })}</option>
          ))}
        </select>
        {exp === 'COUNTER' && (
          <input type="number" min={1} className="field !w-20" value={turns} onChange={(e) => setTurns(e.target.value)} aria-label="Turnos" />
        )}
        {def?.stack === 'SUM' && (
          <input type="number" min={1} className="field !w-20" value={intensity} onChange={(e) => setIntensity(e.target.value)} aria-label="Intensidade" />
        )}
        <select className="field !w-auto" value={source} onChange={(e) => setSource(e.target.value)}>
          <option value="">Sem origem</option>
          {participants.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select>
      </div>
      <input className="field" maxLength={200} placeholder="Nota (opcional)" value={note} onChange={(e) => setNote(e.target.value)} />
      <div className="flex gap-2">
        <button type="submit" className="btn-primary" disabled={!key}>Aplicar</button>
        <button type="button" className="btn-ghost" onClick={onCancel}>Cancelar</button>
      </div>
    </form>
  );
}
