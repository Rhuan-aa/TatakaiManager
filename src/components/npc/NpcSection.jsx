import { useState } from 'react';
import { getCampaignNpc, setNpcVisibility, listOwnedNpcs, associateNpc } from '../../api/npcs';
import { parseApiError } from '../../api/parseApiError';
import CreateNpcForm from './CreateNpcForm';
import EditNpcForm from './EditNpcForm';

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
    <div className="mt-3 rounded-lg border border-zinc-700 bg-zinc-800 p-3 text-sm">
      {npc.description && <p className="text-zinc-300">{npc.description}</p>}

      <p className="mt-2 text-xs text-zinc-500">
        Interações:{' '}
        <span className="text-zinc-400">
          {(npc.interactionTypes ?? []).map((t) => INTERACTION_LABELS[t] ?? t).join(', ')}
        </span>
      </p>

      {attrs.length > 0 && (
        <div className="mt-2">
          <p className="text-xs font-medium text-zinc-500">Atributos</p>
          <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-zinc-300">
            {attrs.map(([k, v]) => (
              <span key={k}>
                {ATTR_LABELS[k] ?? k}: <strong className="text-white">{v}</strong>
              </span>
            ))}
          </div>
        </div>
      )}

      {npc.traits?.length > 0 && (
        <div className="mt-2">
          <p className="text-xs font-medium text-zinc-500">Traços</p>
          <ul className="mt-1 space-y-0.5 text-xs text-zinc-400">
            {npc.traits.map((t, i) => (
              <li key={i}>
                <strong className="text-zinc-300">{t.name}</strong>
                {t.description ? ` — ${t.description}` : ''}
              </li>
            ))}
          </ul>
        </div>
      )}

      {npc.knowledge?.length > 0 && (
        <div className="mt-2">
          <p className="text-xs font-medium text-zinc-500">Conhecimentos</p>
          <ul className="mt-1 space-y-0.5 text-xs text-zinc-400">
            {npc.knowledge.map((k, i) => (
              <li key={i}>
                <strong className="text-zinc-300">{k.name}</strong>
                {k.description ? ` — ${k.description}` : ''}
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
  const [editingId, setEditingId] = useState(null);
  const [details, setDetails] = useState({});
  const [error, setError] = useState('');

  // Acervo — associar NPC existente
  const [associating, setAssociating] = useState(false);
  const [acervo, setAcervo] = useState([]);
  const [acervoLoading, setAcervoLoading] = useState(false);
  const [acervoError, setAcervoError] = useState('');
  const [associatingId, setAssociatingId] = useState(null);

  async function loadDetail(npcId) {
    if (details[npcId]) return details[npcId];
    const detail = await getCampaignNpc(campaignId, npcId);
    setDetails((prev) => ({ ...prev, [npcId]: detail }));
    return detail;
  }

  async function toggleExpand(npcId) {
    setError('');
    if (expandedId === npcId) {
      setExpandedId(null);
      setEditingId(null);
      return;
    }
    setExpandedId(npcId);
    setEditingId(null);
    try {
      await loadDetail(npcId);
    } catch (err) {
      setError(parseApiError(err).message);
    }
  }

  async function handleEditClick(npcId, event) {
    event.stopPropagation();
    setError('');
    try {
      await loadDetail(npcId);
    } catch (err) {
      setError(parseApiError(err).message);
      return;
    }
    setExpandedId(npcId);
    setEditingId(npcId);
  }

  function handleUpdated(updated) {
    setDetails((prev) => ({ ...prev, [updated.id]: updated }));
    setNpcs((prev) =>
      prev.map((n) =>
        n.id === updated.id
          ? { ...n, name: updated.name, interactionTypes: updated.interactionTypes }
          : n
      )
    );
    setEditingId(null);
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

  async function handleOpenAssociate() {
    setAssociating(true);
    setAcervoError('');
    setAcervoLoading(true);
    try {
      const data = await listOwnedNpcs();
      setAcervo(data);
    } catch (err) {
      setAcervoError(parseApiError(err).message);
    } finally {
      setAcervoLoading(false);
    }
  }

  async function handleAssociate(npc) {
    setAssociatingId(npc.id);
    setAcervoError('');
    try {
      await associateNpc(campaignId, npc.id);
      setNpcs((prev) => [
        ...prev,
        { id: npc.id, name: npc.name, visible: true, interactionTypes: npc.interactionTypes },
      ]);
    } catch (err) {
      setAcervoError(parseApiError(err).message);
    } finally {
      setAssociatingId(null);
    }
  }

  function handleCreated(summary) {
    setNpcs((prev) => [...prev, summary]);
    setCreating(false);
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm text-zinc-500">
          {npcs.length} NPC{npcs.length === 1 ? '' : 's'} nesta campanha
        </span>
        {isMaster && !creating && !associating && (
          <div className="flex gap-2">
            <button
              type="button"
              onClick={handleOpenAssociate}
              className="rounded-md border border-zinc-700 px-3 py-1.5 text-sm font-medium text-zinc-300 hover:bg-zinc-800"
            >
              Associar do acervo
            </button>
            <button
              type="button"
              onClick={() => setCreating(true)}
              className="rounded-md bg-red-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-red-700"
            >
              Adicionar NPC
            </button>
          </div>
        )}
      </div>

      {error && <p className="mt-2 text-sm text-red-400">{error}</p>}

      {creating && (
        <div className="mt-4">
          <CreateNpcForm
            campaignId={campaignId}
            onCreated={handleCreated}
            onCancel={() => setCreating(false)}
          />
        </div>
      )}

      {associating && (
        <div className="mt-4 rounded-lg border border-zinc-700 bg-zinc-800 p-4">
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-semibold text-white">Associar NPC do acervo</h4>
            <button
              type="button"
              onClick={() => setAssociating(false)}
              className="text-sm text-zinc-500 hover:text-zinc-300"
            >
              Fechar
            </button>
          </div>
          {acervoLoading && <p className="mt-3 text-sm text-zinc-500">Carregando acervo...</p>}
          {acervoError && <p className="mt-2 text-sm text-red-400">{acervoError}</p>}
          {!acervoLoading && !acervoError && (() => {
            const available = acervo.filter((n) => !npcs.some((existing) => existing.id === n.id));
            if (available.length === 0) {
              return (
                <p className="mt-3 text-sm text-zinc-500">
                  {acervo.length === 0
                    ? 'Você ainda não tem NPCs no acervo.'
                    : 'Todos os NPCs do acervo já estão nesta campanha.'}
                </p>
              );
            }
            return (
              <ul className="mt-3 space-y-2">
                {available.map((npc) => (
                  <li
                    key={npc.id}
                    className="flex items-center justify-between gap-3 rounded-md border border-zinc-700 px-3 py-2"
                  >
                    <div>
                      <span className="text-sm font-medium text-white">{npc.name}</span>
                      <span className="ml-2 text-xs text-zinc-500">
                        {[...npc.interactionTypes]
                          .map((t) => INTERACTION_LABELS[t] ?? t)
                          .join(', ')}
                      </span>
                    </div>
                    <button
                      type="button"
                      disabled={associatingId === npc.id}
                      onClick={() => handleAssociate(npc)}
                      className="rounded-md bg-red-600 px-2 py-1 text-xs font-semibold text-white hover:bg-red-700 disabled:opacity-50"
                    >
                      {associatingId === npc.id ? 'Associando...' : 'Associar'}
                    </button>
                  </li>
                ))}
              </ul>
            );
          })()}
        </div>
      )}

      {npcs.length === 0 ? (
        <p className="mt-4 text-sm text-zinc-500">Nenhum NPC nesta campanha ainda.</p>
      ) : (
        <ul className="mt-3 space-y-2">
          {npcs.map((npc) => (
            <li key={npc.id} className="rounded-lg border border-zinc-700 bg-zinc-800">
              <div className="flex items-center justify-between gap-3 p-3">
                <button
                  type="button"
                  onClick={() => toggleExpand(npc.id)}
                  className="text-left text-sm font-medium text-zinc-200 hover:text-red-400 transition"
                >
                  {expandedId === npc.id ? '▾' : '▸'} {npc.name}
                </button>
                {isMaster && (
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={(e) => handleEditClick(npc.id, e)}
                      className="text-xs text-zinc-500 hover:text-red-400"
                    >
                      Editar
                    </button>
                    <button
                      type="button"
                      onClick={(e) => toggleVisibility(npc, e)}
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                        npc.visible
                          ? 'bg-green-950 text-green-400 border border-green-900'
                          : 'bg-zinc-700 text-zinc-500'
                      }`}
                      title="Alternar visibilidade para os jogadores"
                    >
                      {npc.visible ? 'Visível' : 'Oculto'}
                    </button>
                  </div>
                )}
              </div>

              {expandedId === npc.id && (
                <div className="border-t border-zinc-700 px-3 pb-3">
                  {editingId === npc.id && details[npc.id] ? (
                    <EditNpcForm
                      npc={details[npc.id]}
                      onUpdated={handleUpdated}
                      onCancel={() => setEditingId(null)}
                    />
                  ) : details[npc.id] ? (
                    <NpcDetail npc={details[npc.id]} />
                  ) : null}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
