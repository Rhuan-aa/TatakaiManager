import { useEffect, useState } from 'react';
import { getTraining } from '../../api/characterTechniques';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';

const TARGETS = {
  STR: 'Força', DEX: 'Destreza', CON: 'Constituição', INT: 'Inteligência', CHA: 'Carisma', MEN: 'Mental',
  HP: 'HP máx.', SP: 'SP máx.', KP: 'PQ máx.', TECHNIQUE: 'Técnica',
};
const STATUS = { PENDING: 'Pendente', CONSOLIDATED: 'Consolidado', APPLIED: 'Aplicado', LOST: 'Perdido' };

/** Livro de treinos (F4a): ganhos pendentes até o Repouso e histórico. */
export default function TrainingPanel({ sheetId, refreshKey }) {
  const toast = useToast();
  const [entries, setEntries] = useState(null);

  useEffect(() => {
    let active = true;
    getTraining(sheetId).then((e) => active && setEntries(e))
      .catch((err) => active && toast(parseApiError(err).message, 'error'));
    return () => { active = false; };
  }, [sheetId, refreshKey, toast]);

  if (!entries) return <div className="skeleton h-12 w-full" />;
  if (entries.length === 0) return <p className="text-sm">Nenhum treino ainda. Agende Treino e Repouso na Agenda do TimeSkip.</p>;
  const pending = entries.filter((e) => e.status === 'PENDING');
  return (
    <div className="flex flex-col gap-3">
      {pending.length > 0 && (
        <p className="caption self-start">
          Pendentes até o próximo Repouso: {pending.map((e) => `${TARGETS[e.target] ?? e.target} +${e.gain}`).join(', ')}
        </p>
      )}
      <ul className="flex flex-col gap-1 text-sm">
        {[...entries].reverse().slice(0, 30).map((e) => (
          <li key={e.id} className="flex flex-wrap items-center gap-2">
            <span className="tag">Dia {e.dayNumber}</span>
            <strong>{TARGETS[e.target] ?? e.target}</strong>
            <span>+{e.gain}</span>
            {!e.full && <span className="tag">Parcial</span>}
            <span className={e.status === 'PENDING' ? 'tag-accent tag' : 'tag'}>{STATUS[e.status]}</span>
            {e.note && <span className="text-fine opacity-70">{e.note}</span>}
          </li>
        ))}
      </ul>
    </div>
  );
}
