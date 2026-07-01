import { useEffect, useState } from 'react';
import {
  getCampaignNpc,
  setNpcVisibility,
  listOwnedNpcs,
  associateNpc,
  removeNpcFromCampaign,
  fetchNpcImageUrl,
} from '../../api/npcs';
import { parseApiError } from '../../api/parseApiError';
import CreateNpcForm from './CreateNpcForm';
import EditNpcForm from './EditNpcForm';

const ATTR_LABELS = {
  forca: 'Força',
  destreza: 'Destreza',
  constituicao: 'Constituição',
  mental: 'Mental',
  inteligencia: 'Inteligência',
  talento: 'Talento',
};

function NpcDetail({ npc, campaignId }) {
  const attrs = npc.attributes
    ? Object.entries(npc.attributes).filter(([, v]) => v != null)
    : [];

  const [imageUrl, setImageUrl] = useState(null);

  useEffect(() => {
    if (!npc.hasImage) {
      setImageUrl(null);
      return;
    }
    let url;
    let active = true;
    fetchNpcImageUrl(campaignId, npc.id)
      .then((u) => {
        url = u;
        if (active) setImageUrl(u);
        else URL.revokeObjectURL(u);
      })
      .catch(() => {});
    return () => {
      active = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [campaignId, npc.id, npc.hasImage]);

  return (
    <div className="mt-3 rounded-lg border border-zinc-700 bg-zinc-800 p-3 text-sm">
      {imageUrl && (
        <img
          src={imageUrl}
          alt={npc.name}
          className="mb-3 max-h-64 w-full rounded-md object-cover"
        />
      )}
      {npc.description && <p className="text-zinc-300">{npc.description}</p>}

      {npc.interactions?.length > 0 && (
        <div className="mt-2">
          <p className="text-xs font-medium text-zinc-500">Interações</p>
          <ul className="mt-1 space-y-0.5 text-xs text-zinc-400">
            {npc.interactions.map((it, i) => (
              <li key={i}>
                {it.type ? <span className="text-zinc-500">[{it.type}] </span> : ''}
                <strong className="text-zinc-300">{it.name}</strong>
                <span className="text-red-400"> · {it.idlePointCost} pts de ócio</span>
                {it.description ? ` — ${it.description}` : ''}
              </li>
            ))}
          </ul>
        </div>
      )}

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

      {npc.specs?.length > 0 && (
        <div className="mt-2">
          <p className="text-xs font-medium text-zinc-500">Specs</p>
          <ul className="mt-1 space-y-0.5 text-xs text-zinc-400">
            {npc.specs.map((s, i) => (
              <li key={i}>
                <strong className="text-zinc-300">{s.name}</strong>
                {s.description ? ` — ${s.description}` : ''}
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
  const [confirmRemoveId, setConfirmRemoveId] = useState(null);
  const [removingId, setRemovingId] = useState(null);

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
          ? { ...n, name: updated.name, interactions: updated.interactions }
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

  async function handleRemove(npc, event) {
    event.stopPropagation();
    setError('');
    setRemovingId(npc.id);
    try {
      await removeNpcFromCampaign(campaignId, npc.id);
      setNpcs((prev) => prev.filter((n) => n.id !== npc.id));
      setDetails((prev) => {
        const next = { ...prev };
        delete next[npc.id];
        return next;
      });
      if (expandedId === npc.id) setExpandedId(null);
      setConfirmRemoveId(null);
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setRemovingId(null);
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
        { id: npc.id, name: npc.name, visible: true, interactions: npc.interactions },
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
                        {(npc.interactions ?? []).map((i) => i.name).join(', ')}
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
                    {confirmRemoveId === npc.id ? (
                      <span className="flex items-center gap-1">
                        <button
                          type="button"
                          onClick={(e) => handleRemove(npc, e)}
                          disabled={removingId === npc.id}
                          className="rounded-md bg-red-600 px-2 py-0.5 text-xs font-semibold text-white hover:bg-red-700 disabled:opacity-50"
                        >
                          {removingId === npc.id ? 'Removendo...' : 'Confirmar'}
                        </button>
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setConfirmRemoveId(null);
                          }}
                          className="text-xs text-zinc-500 hover:text-zinc-300"
                        >
                          Cancelar
                        </button>
                      </span>
                    ) : (
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          setConfirmRemoveId(npc.id);
                        }}
                        className="text-xs text-zinc-600 hover:text-red-400"
                        title="Remover NPC desta campanha"
                      >
                        Remover
                      </button>
                    )}
                  </div>
                )}
              </div>

              {expandedId === npc.id && (
                <div className="border-t border-zinc-700 px-3 pb-3">
                  {editingId === npc.id && details[npc.id] ? (
                    <EditNpcForm
                      npc={details[npc.id]}
                      campaignId={campaignId}
                      onUpdated={handleUpdated}
                      onCancel={() => setEditingId(null)}
                    />
                  ) : details[npc.id] ? (
                    <NpcDetail npc={details[npc.id]} campaignId={campaignId} />
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
