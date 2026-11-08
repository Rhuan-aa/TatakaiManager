import { useState } from 'react';

/** Linha do tempo recolhida, com filtro por participante. */
export default function Timeline({ events, participants = [] }) {
  const [who, setWho] = useState('');
  const shown = who ? events.filter((e) => e.participantId === who) : events;
  return (
    <details>
      <summary className="panel-title cursor-pointer text-lg">Linha do tempo ({events.length})</summary>
      <div className="mt-2 space-y-2">
        {participants.length > 1 && (
          <select className="field !w-auto !py-0.5 text-xs" aria-label="Filtrar por participante" value={who}
            onChange={(e) => setWho(e.target.value)}>
            <option value="">Todos os participantes</option>
            {participants.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
        )}
        {!shown.length ? <p className="caption">Nenhum evento{who ? ' deste participante' : ' ainda'}.</p> : (
          <ul className="max-h-[60vh] space-y-1 overflow-y-auto text-sm">
            {[...shown].reverse().map((e) => (
              <li key={e.id} className="border-l-4 border-[var(--line)] pl-2">
                <span className="tag mr-2">R{e.round}</span>{e.text}
              </li>
            ))}
          </ul>
        )}
      </div>
    </details>
  );
}
