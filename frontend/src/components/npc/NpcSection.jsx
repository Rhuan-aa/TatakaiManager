import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  getCampaignNpc,
  setNpcVisibility,
  listOwnedNpcs,
  associateNpc,
  removeNpcFromCampaign,
  fetchNpcImageUrl,
} from '../../api/npcs';
import { parseApiError } from '../../api/parseApiError';
import { EmptyState } from '../../components/layout/AppShell';
import { useToast } from '../../contexts/ToastContext';
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

/** Retrato (retângulo vertical) do NPC, com placeholder quando não há imagem. */
function NpcPortrait({ campaignId, npcId, hasImage, name, className = '' }) {
  const [imageUrl, setImageUrl] = useState(null);

  useEffect(() => {
    if (!hasImage) {
      setImageUrl(null);
      return undefined;
    }
    let url;
    let active = true;
    fetchNpcImageUrl(campaignId, npcId)
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
  }, [campaignId, npcId, hasImage]);

  if (imageUrl) {
    return (
      <img
        src={imageUrl}
        alt={name}
        className={`h-full w-full object-cover object-top ${className}`}
      />
    );
  }
  return (
    <div
      className={`flex h-full w-full items-center justify-center bg-gradient-to-br from-zinc-800 to-zinc-900 ${className}`}
    >
      <span className="select-none text-5xl font-black text-zinc-700">
        {name?.charAt(0)?.toUpperCase() ?? '?'}
      </span>
    </div>
  );
}

/** Card compacto do NPC (retrato 3:4), 2 por linha. */
function NpcCard({ npc, campaignId, isMaster, onOpen, onToggleVisibility }) {
  const hidden = isMaster && !npc.visible;
  return (
    <button
      type="button"
      onClick={() => onOpen(npc)}
      className="group relative overflow-hidden rounded-xl border border-zinc-800 bg-zinc-900 text-left shadow-sm ring-red-500/0 transition-all duration-200 hover:-translate-y-0.5 hover:border-zinc-700 hover:shadow-lg hover:shadow-black/40 hover:ring-2 hover:ring-red-500/30 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500"
    >
      <div className="relative aspect-[3/4] w-full overflow-hidden">
        <div className={hidden ? 'h-full w-full opacity-40 grayscale' : 'h-full w-full'}>
          <NpcPortrait
            campaignId={campaignId}
            npcId={npc.id}
            hasImage={npc.hasImage}
            name={npc.name}
            className="transition-transform duration-300 group-hover:scale-105"
          />
        </div>

        {/* Gradiente + nome */}
        <div className="pointer-events-none absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 via-black/40 to-transparent p-3 pt-8">
          {/* Nome sobre overlay escuro da imagem — sempre branco (independe do tema) */}
          <p className="truncate text-sm font-semibold text-white">{npc.name}</p>
          {npc.interactions?.length > 0 && (
            <p className="truncate text-[11px] text-zinc-400">
              {npc.interactions.length} interaç{npc.interactions.length === 1 ? 'ão' : 'ões'}
            </p>
          )}
        </div>

        {/* Selo de visibilidade (Mestre) */}
        {isMaster && (
          <span
            role="button"
            tabIndex={0}
            onClick={(e) => {
              e.stopPropagation();
              onToggleVisibility(npc, e);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                e.stopPropagation();
                onToggleVisibility(npc, e);
              }
            }}
            title="Alternar visibilidade para os jogadores"
            className={`absolute right-2 top-2 cursor-pointer rounded-full px-2 py-0.5 text-[11px] font-semibold backdrop-blur transition ${
              npc.visible
                ? 'border border-green-800/60 bg-green-950/70 text-green-400'
                : 'border border-zinc-700 bg-zinc-900/80 text-zinc-400'
            }`}
          >
            {npc.visible ? 'Visível' : 'Oculto'}
          </span>
        )}
      </div>
    </button>
  );
}

function DetailList({ title, items, render }) {
  if (!items?.length) return null;
  return (
    <div>
      <p className="text-xs font-semibold uppercase tracking-wide text-zinc-500">{title}</p>
      <ul className="mt-1.5 space-y-1 text-sm text-zinc-300">
        {items.map((it, i) => (
          <li key={i}>{render(it)}</li>
        ))}
      </ul>
    </div>
  );
}

/** Conteúdo do detalhe do NPC exibido no modal. */
function NpcDetailView({ npc, campaignId }) {
  const attrs = npc.attributes
    ? Object.entries(npc.attributes).filter(([, v]) => v != null)
    : [];

  return (
    <div className="flex flex-col gap-5 sm:flex-row">
      {/* Retrato adaptado ao modal: mesma proporção 3:4 do card, porém maior,
          alinhado ao topo e integrado (ring/sombra) — sem "flutuar". */}
      <div className="w-40 shrink-0 self-start overflow-hidden rounded-xl border border-zinc-800 shadow-lg shadow-black/40 ring-1 ring-white/5 sm:w-52">
        <div className="aspect-[3/4]">
          <NpcPortrait
            campaignId={campaignId}
            npcId={npc.id}
            hasImage={npc.hasImage}
            name={npc.name}
          />
        </div>
      </div>

      <div className="min-w-0 flex-1 space-y-4">
        {npc.description && <p className="text-sm text-zinc-300">{npc.description}</p>}

        {npc.interactions?.length > 0 && (
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-zinc-500">Interações</p>
            <ul className="mt-1.5 space-y-1 text-sm text-zinc-300">
              {npc.interactions.map((it, i) => (
                <li key={i} className="flex flex-wrap items-baseline gap-x-1.5">
                  {it.type && (
                    <span className="rounded bg-zinc-800 px-1.5 py-0.5 text-[11px] font-medium text-zinc-400">
                      {it.type}
                    </span>
                  )}
                  <strong className="text-zinc-100">{it.name}</strong>
                  <span className="text-xs font-semibold text-red-400">
                    {it.idlePointCost} pts de ócio
                  </span>
                  {it.description && <span className="text-xs text-zinc-500">— {it.description}</span>}
                </li>
              ))}
            </ul>
          </div>
        )}

        {attrs.length > 0 && (
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-zinc-500">Atributos</p>
            <div className="mt-1.5 flex flex-wrap gap-2">
              {attrs.map(([k, v]) => (
                <span
                  key={k}
                  className="rounded-md border border-zinc-800 bg-zinc-800/60 px-2 py-1 text-xs text-zinc-300"
                >
                  {ATTR_LABELS[k] ?? k}: <strong className="text-zinc-50">{v}</strong>
                </span>
              ))}
            </div>
          </div>
        )}

        <DetailList
          title="Traços"
          items={npc.traits}
          render={(t) => (
            <>
              <strong className="text-zinc-100">{t.name}</strong>
              {t.description ? <span className="text-zinc-500"> — {t.description}</span> : ''}
            </>
          )}
        />
        <DetailList
          title="Specs"
          items={npc.specs}
          render={(s) => (
            <>
              <strong className="text-zinc-100">{s.name}</strong>
              {s.description ? <span className="text-zinc-500"> — {s.description}</span> : ''}
            </>
          )}
        />
        <DetailList
          title="Conhecimentos"
          items={npc.knowledge}
          render={(k) => (
            <>
              <strong className="text-zinc-100">{k.name}</strong>
              {k.description ? <span className="text-zinc-500"> — {k.description}</span> : ''}
            </>
          )}
        />
      </div>
    </div>
  );
}

/** Overlay modal responsivo (fecha no backdrop e no Esc). */
function Modal({ title, onClose, children }) {
  useEffect(() => {
    function onKey(e) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [onClose]);

  // Portal para o body: escapa de qualquer ancestral com backdrop-filter/transform
  // (ex.: a .surface), que de outra forma vira o containing block do position:fixed.
  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/70 p-0 backdrop-blur-sm animate-[fade-in_120ms_ease-out] sm:items-center sm:p-4"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="flex max-h-[90vh] w-full max-w-2xl flex-col overflow-hidden rounded-t-2xl border border-zinc-800 bg-zinc-900 shadow-2xl animate-[pop-in_160ms_ease-out] sm:rounded-2xl"
      >
        <div className="flex items-center justify-between gap-3 border-b border-zinc-800 px-5 py-3">
          <h3 className="truncate text-base font-semibold text-zinc-50">{title}</h3>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md p-1 text-zinc-500 hover:bg-zinc-800 hover:text-zinc-200"
            aria-label="Fechar"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div className="overflow-y-auto px-5 py-4">{children}</div>
      </div>
    </div>,
    document.body
  );
}

export default function NpcSection({ campaignId, isMaster, npcs, setNpcs }) {
  const toast = useToast();
  const [creating, setCreating] = useState(false);
  const [selectedId, setSelectedId] = useState(null);
  const [editing, setEditing] = useState(false);
  const [details, setDetails] = useState({});
  const [error, setError] = useState('');
  const [confirmRemove, setConfirmRemove] = useState(false);
  const [removing, setRemoving] = useState(false);

  // Acervo — associar NPC existente
  const [associating, setAssociating] = useState(false);
  const [acervo, setAcervo] = useState([]);
  const [acervoLoading, setAcervoLoading] = useState(false);
  const [acervoError, setAcervoError] = useState('');
  const [associatingId, setAssociatingId] = useState(null);

  const selected = npcs.find((n) => n.id === selectedId) ?? null;
  const selectedDetail = selectedId ? details[selectedId] : null;

  async function loadDetail(npcId) {
    if (details[npcId]) return details[npcId];
    const detail = await getCampaignNpc(campaignId, npcId);
    setDetails((prev) => ({ ...prev, [npcId]: detail }));
    return detail;
  }

  async function openNpc(npc) {
    setError('');
    setEditing(false);
    setConfirmRemove(false);
    setSelectedId(npc.id);
    try {
      await loadDetail(npc.id);
    } catch (err) {
      setError(parseApiError(err).message);
    }
  }

  function closeModal() {
    setSelectedId(null);
    setEditing(false);
    setConfirmRemove(false);
  }

  function handleUpdated(updated) {
    setDetails((prev) => ({ ...prev, [updated.id]: updated }));
    setNpcs((prev) =>
      prev.map((n) =>
        n.id === updated.id
          ? { ...n, name: updated.name, interactions: updated.interactions, hasImage: updated.hasImage }
          : n
      )
    );
    setEditing(false);
    toast(`NPC "${updated.name}" atualizado.`);
  }

  async function toggleVisibility(npc) {
    setError('');
    try {
      const res = await setNpcVisibility(campaignId, npc.id, !npc.visible);
      setNpcs((prev) => prev.map((n) => (n.id === npc.id ? { ...n, visible: res.visible } : n)));
      toast(res.visible ? 'NPC visível aos jogadores.' : 'NPC ocultado dos jogadores.');
    } catch (err) {
      setError(parseApiError(err).message);
    }
  }

  async function handleRemove(npc) {
    setError('');
    setRemoving(true);
    try {
      await removeNpcFromCampaign(campaignId, npc.id);
      setNpcs((prev) => prev.filter((n) => n.id !== npc.id));
      setDetails((prev) => {
        const next = { ...prev };
        delete next[npc.id];
        return next;
      });
      closeModal();
      toast(`NPC "${npc.name}" removido da campanha.`);
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setRemoving(false);
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
        {
          id: npc.id,
          name: npc.name,
          visible: true,
          hasImage: npc.hasImage,
          interactions: npc.interactions,
        },
      ]);
      toast(`"${npc.name}" associado à campanha.`);
    } catch (err) {
      setAcervoError(parseApiError(err).message);
    } finally {
      setAssociatingId(null);
    }
  }

  function handleCreated(summary) {
    setNpcs((prev) => [...prev, summary]);
    setCreating(false);
    toast(`NPC "${summary.name}" criado.`);
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
              className="rounded-lg border border-zinc-700 px-3 py-1.5 text-sm font-medium text-zinc-300 transition hover:border-zinc-600 hover:bg-zinc-800"
            >
              Associar do acervo
            </button>
            <button
              type="button"
              onClick={() => setCreating(true)}
              className="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-semibold text-white shadow-sm transition hover:bg-red-700"
            >
              + Adicionar NPC
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
        <div className="mt-4 rounded-xl border border-zinc-800 bg-zinc-900 p-4">
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-semibold text-zinc-50">Associar NPC do acervo</h4>
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
                    className="flex items-center justify-between gap-3 rounded-lg border border-zinc-800 px-3 py-2"
                  >
                    <div className="min-w-0">
                      <span className="text-sm font-medium text-zinc-50">{npc.name}</span>
                      <span className="ml-2 text-xs text-zinc-500">
                        {(npc.interactions ?? []).map((i) => i.name).join(', ')}
                      </span>
                    </div>
                    <button
                      type="button"
                      disabled={associatingId === npc.id}
                      onClick={() => handleAssociate(npc)}
                      className="shrink-0 rounded-md bg-red-600 px-2.5 py-1 text-xs font-semibold text-white hover:bg-red-700 disabled:opacity-50"
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
        <EmptyState
          className="mt-4"
          icon="🎭"
          title="Nenhum NPC nesta campanha"
          description={
            isMaster
              ? 'Crie um novo NPC ou associe um do seu acervo para começar.'
              : 'O Mestre ainda não adicionou NPCs visíveis nesta campanha.'
          }
          action={
            isMaster && !creating && !associating ? (
              <button
                type="button"
                onClick={() => setCreating(true)}
                className="rounded-lg bg-gradient-to-b from-red-500 to-red-600 px-3 py-1.5 text-sm font-semibold text-white shadow-lg shadow-red-950/40 transition hover:to-red-700"
              >
                + Adicionar NPC
              </button>
            ) : null
          }
        />
      ) : (
        <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 lg:grid-cols-4 xl:grid-cols-5">
          {npcs.map((npc) => (
            <NpcCard
              key={npc.id}
              npc={npc}
              campaignId={campaignId}
              isMaster={isMaster}
              onOpen={openNpc}
              onToggleVisibility={toggleVisibility}
            />
          ))}
        </div>
      )}

      {selected && (
        <Modal title={selected.name} onClose={closeModal}>
          {editing && selectedDetail ? (
            <EditNpcForm
              npc={selectedDetail}
              campaignId={campaignId}
              onUpdated={handleUpdated}
              onCancel={() => setEditing(false)}
            />
          ) : selectedDetail ? (
            <>
              <NpcDetailView npc={selectedDetail} campaignId={campaignId} />
              {isMaster && (
                <div className="mt-5 flex flex-wrap items-center gap-2 border-t border-zinc-800 pt-4">
                  <button
                    type="button"
                    onClick={() => setEditing(true)}
                    className="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-red-700"
                  >
                    Editar
                  </button>
                  <button
                    type="button"
                    onClick={() => toggleVisibility(selected)}
                    className={`rounded-lg px-3 py-1.5 text-sm font-medium ${
                      selected.visible
                        ? 'border border-green-800/60 bg-green-950/50 text-green-400'
                        : 'border border-zinc-700 text-zinc-400 hover:bg-zinc-800'
                    }`}
                  >
                    {selected.visible ? 'Visível aos jogadores' : 'Oculto dos jogadores'}
                  </button>
                  {confirmRemove ? (
                    <span className="ml-auto flex items-center gap-2">
                      <span className="text-xs text-red-400">Remover da campanha?</span>
                      <button
                        type="button"
                        onClick={() => handleRemove(selected)}
                        disabled={removing}
                        className="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50"
                      >
                        {removing ? 'Removendo...' : 'Confirmar'}
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmRemove(false)}
                        className="text-sm text-zinc-500 hover:text-zinc-300"
                      >
                        Cancelar
                      </button>
                    </span>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setConfirmRemove(true)}
                      className="ml-auto rounded-lg border border-red-900/60 px-3 py-1.5 text-sm font-medium text-red-400 hover:bg-red-950/40"
                    >
                      Remover
                    </button>
                  )}
                </div>
              )}
            </>
          ) : (
            <p className="py-6 text-center text-sm text-zinc-500">Carregando...</p>
          )}
        </Modal>
      )}
    </div>
  );
}
