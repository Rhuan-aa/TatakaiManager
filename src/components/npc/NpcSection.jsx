import { useState } from 'react';
import { getCampaignNpc, setNpcVisibility } from '../../api/npcs';
import { parseApiError } from '../../api/parseApiError';
import CreateNpcForm from './CreateNpcForm';

const INTERACTION_LABELS = {
  TREINO: 'Treino',
  TRABALHO: 'Trabalho',
  DESCANSO: 'Descanso',
  OUTRO: 'Outro',
};

const ATTR_LABELS = {
  forca: 'Força',
  destreza: 'Destreza',
  constituicao: 'Constituição',
  mental: 'Mental',
  inteligencia: 'Inteligência',
  talento: 'Talento',
};

function NpcDetail({ npc }) {
  const attrs = npc.attributes
    ? Object.entries(npc.attributes).filter(([, v]) => v != null)
    : [];

  return (
    <div className="mt-2 rounded-md bg-slate-50 p-3 text-sm">
      {npc.description && <p className="text-slate-700">{npc.description}</p>}

      <p className="mt-2 text-xs text-slate-500">
        Interações:{' '}
        {(npc.interactionTypes ?? []).map((t) => INTERACTION_LABELS[t] ?? t).join(', ')}
      </p>

      {attrs.length > 0 && (
        <div className="mt-2">
          <p className="text-xs font-medium text-slate-500">Atributos</p>
          <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-700">
            {attrs.map(([k, v]) => (
              <span key={k}>
                {ATTR_LABELS[k] ?? k}: <strong>{v}</strong>
              </span>
            ))}
          </div>
        </div>
      )}

      {npc.traits?.length > 0 && (
        <p className="mt-2 text-xs text-slate-700">
          <span className="font-medium text-slate-500">Traços:</span> {npc.traits.join(', ')}
        </p>
      )}

      {npc.specs?.length > 0 && (
        <div className="mt-2">
          <p className="text-xs font-medium text-slate-500">Especializações</p>
          <ul className="mt-1 space-y-0.5 text-xs text-slate-700">
            {npc.specs.map((s, i) => (
              <li key={i}>
                <strong>{s.name}</strong>
                {s.description ? ` — ${s.description}` : ''}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export default function NpcSection({ campaignId, isMaster, npcs, setNpcs }) {
  const [creating, setCreating] = useState(false);
  const [expandedId, setExpandedId] = useState(null);
  const [details, setDetails] = useState({});
  const [error, setError] = useState('');

  async function toggleExpand(npcId) {
    setError('');
    if (expandedId === npcId) {
      setExpandedId(null);
      return;
    }
    setExpandedId(npcId);
    if (!details[npcId]) {
      try {
        const detail = await getCampaignNpc(campaignId, npcId);
        setDetails((prev) => ({ ...prev, [npcId]: detail }));
      } catch (err) {
        setError(parseApiError(err).message);
      }
    }
  }

  async function toggleVisibility(npc, event) {
    event.stopPropagation();
    setError('');
    try {
      const res = await setNpcVisibility(campaignId, npc.id, !npc.visible);
      setNpcs((prev) => prev.map((n) => (n.id === npc.id ? { ...n, visible: res.visible } : n)));
    } catch (err) {
      setError(parseApiError(err).message);
    }
  }

  function handleCreated(summary) {
    setNpcs((prev) => [...prev, summary]);
    setCreating(false);
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <span className="text-sm text-slate-500">
          {npcs.length} NPC{npcs.length === 1 ? '' : 's'} nesta campanha
        </span>
        {isMaster && !creating && (
          <button
            type="button"
            onClick={() => setCreating(true)}
            className="rounded-md bg-purple-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-purple-700"
          >
            Adicionar NPC
          </button>
        )}
      </div>

      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}

      {creating && (
        <div className="mt-4">
          <CreateNpcForm
            campaignId={campaignId}
            onCreated={handleCreated}
            onCancel={() => setCreating(false)}
          />
        </div>
      )}

      {npcs.length === 0 ? (
        <p className="mt-3 text-sm text-slate-500">Nenhum NPC nesta campanha ainda.</p>
      ) : (
        <ul className="mt-3 space-y-2">
          {npcs.map((npc) => (
            <li key={npc.id} className="rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
              <div className="flex items-center justify-between gap-3">
                <button
                  type="button"
                  onClick={() => toggleExpand(npc.id)}
                  className="text-left font-medium text-slate-900 hover:text-purple-700"
                >
                  {npc.name}
                </button>
                {isMaster && (
                  <button
                    type="button"
                    onClick={(e) => toggleVisibility(npc, e)}
                    className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                      npc.visible
                        ? 'bg-green-100 text-green-700 hover:bg-green-200'
                        : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                    }`}
                    title="Alternar visibilidade para os jogadores"
                  >
                    {npc.visible ? 'Visível' : 'Oculto'}
                  </button>
                )}
              </div>
              {expandedId === npc.id && details[npc.id] && <NpcDetail npc={details[npc.id]} />}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
