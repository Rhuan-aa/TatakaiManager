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
import NpcSheetPanel from '../sheet/NpcSheetPanel';

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
      className={`flex h-full w-full items-center justify-center bg-[var(--panel-bg)] halftone ${className}`}
    >
      <span className="select-none text-5xl font-black text-[var(--text)] opacity-70">
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
      className="panel group relative overflow-hidden text-left transition-transform duration-150 hover:-translate-y-0.5 focus-visible:outline-[3px] focus-visible:outline-[var(--accent)]"
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
        <div className="pointer-events-none absolute inset-x-0 bottom-0 border-t-[length:var(--border-w)] border-[var(--line)] bg-[var(--ink)] p-2">
          {/* Nome sobre overlay escuro da imagem — sempre branco (independe do tema) */}
          <p className="truncate text-sm font-semibold text-white">{npc.name}</p>
          {npc.sheetId && <span className="tag mt-1">Ficha</span>}
          {npc.interactions?.length > 0 && (
            <p className="truncate text-[11px] text-white opacity-70">
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
            className={`absolute right-2 top-2 cursor-pointer ${
              npc.visible ? 'tag-accent' : 'tag'
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
      <p className="text-xs font-semibold uppercase tracking-wide text-[var(--text)] opacity-70">{title}</p>
      <ul className="mt-1.5 space-y-1 text-sm text-[var(--text)]">
        {items.map((it, i) => (
          <li key={i}>{render(it)}</li>
        ))}
      </ul>
    </div>
  );
}

/** Conteúdo do detalhe do NPC exibido no modal. */
function NpcDetailView({ npc, campaignId }) {
  const attrs = npc.attributes && !npc.sheetId
    ? Object.entries(npc.attributes).filter(([, v]) => v != null)
    : [];

  return (
    <div className="flex flex-col gap-5 sm:flex-row">
      {/* Retrato adaptado ao modal: mesma proporção 3:4 do card, porém maior,
          alinhado ao topo e integrado (ring/sombra) — sem "flutuar". */}
      <div className="w-40 shrink-0 self-start panel overflow-hidden sm:w-52">
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
        {npc.description && <p className="text-sm text-[var(--text)]">{npc.description}</p>}

        {npc.interactions?.length > 0 && (
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-[var(--text)] opacity-70">Interações</p>
            <ul className="mt-1.5 space-y-1 text-sm text-[var(--text)]">
              {npc.interactions.map((it, i) => (
                <li key={i} className="flex flex-wrap items-baseline gap-x-1.5">
                  {it.type && (
                    <span className="tag">
                      {it.type}
                    </span>
                  )}
                  <strong className="text-[var(--text)]">{it.name}</strong>
                  <span className="text-xs font-semibold text-[var(--accent)]">
                    {it.idlePointCost} pts de ócio
                  </span>
                  {it.description && <span className="text-xs text-[var(--text)] opacity-70">— {it.description}</span>}
                </li>
              ))}
            </ul>
          </div>
        )}

        {attrs.length > 0 && (
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-[var(--text)] opacity-70">Atributos</p>
            <div className="mt-1.5 flex flex-wrap gap-2">
              {attrs.map(([k, v]) => (
                <span
                  key={k}
                  className="tag"
                >
                  {ATTR_LABELS[k] ?? k}: <strong className="text-[var(--text)]">{v}</strong>
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
              <strong className="text-[var(--text)]">{t.name}</strong>
              {t.description ? <span className="text-[var(--text)] opacity-70"> — {t.description}</span> : ''}
            </>
          )}
        />
        <DetailList
          title="Specs"
          items={npc.specs}
          render={(s) => (
            <>
              <strong className="text-[var(--text)]">{s.name}</strong>
              {s.description ? <span className="text-[var(--text)] opacity-70"> — {s.description}</span> : ''}
            </>
          )}
        />
        <DetailList
          title="Conhecimentos"
          items={npc.knowledge}
          render={(k) => (
            <>
              <strong className="text-[var(--text)]">{k.name}</strong>
              {k.description ? <span className="text-[var(--text)] opacity-70"> — {k.description}</span> : ''}
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
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/80 p-0 animate-[fade-in_120ms_ease-out] sm:items-center sm:p-4"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="flex max-h-[90vh] w-full max-w-2xl flex-col panel overflow-hidden animate-[pop-in_160ms_ease-out]"
      >
        <div className="flex items-center justify-between gap-3 border-b-[length:var(--border-w)] border-[var(--line)] px-5 py-3">
          <h3 className="panel-title truncate text-xl">{title}</h3>
          <button
            type="button"
            onClick={onClose}
            className="btn-ghost p-1"
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
          ? { ...n, name: updated.name, interactions: updated.interactions, hasImage: updated.hasImage, sheetId: updated.sheetId }
          : n
      )
    );
    setEditing(false);
    toast(`NPC "${updated.name}" atualizado.`);
  }

  function handleSheetChanged(sheet) {
    const sheetId = sheet?.id ?? null;
    setDetails((prev) => ({ ...prev, [selectedId]: { ...prev[selectedId], sheetId } }));
    setNpcs((prev) => prev.map((n) => (n.id === selectedId ? { ...n, sheetId } : n)));
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
        <span className="text-sm text-[var(--text)] opacity-70">
          {npcs.length} NPC{npcs.length === 1 ? '' : 's'} nesta campanha
        </span>
        {isMaster && !creating && !associating && (
          <div className="flex gap-2">
            <button
              type="button"
              onClick={handleOpenAssociate}
              className="btn-secondary text-sm"
            >
              Associar do acervo
            </button>
            <button
              type="button"
              onClick={() => setCreating(true)}
              className="btn-primary text-sm"
            >
              + Adicionar NPC
            </button>
          </div>
        )}
      </div>

      {error && <p className="mt-2 text-sm text-[var(--accent)]">{error}</p>}

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
        <div className="mt-4 panel p-4">
          <div className="flex items-center justify-between">
            <h4 className="panel-title text-xl">Associar NPC do acervo</h4>
            <button
              type="button"
              onClick={() => setAssociating(false)}
              className="btn-ghost text-sm"
            >
              Fechar
            </button>
          </div>
          {acervoLoading && <p className="mt-3 text-sm text-[var(--text)] opacity-70">Carregando acervo...</p>}
          {acervoError && <p className="mt-2 text-sm text-[var(--accent)]">{acervoError}</p>}
          {!acervoLoading && !acervoError && (() => {
            const available = acervo.filter((n) => !npcs.some((existing) => existing.id === n.id));
            if (available.length === 0) {
              return (
                <p className="mt-3 text-sm text-[var(--text)] opacity-70">
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
                    className="flex items-center justify-between gap-3 panel px-3 py-2"
                  >
                    <div className="min-w-0">
                      <span className="text-sm font-medium text-[var(--text)]">{npc.name}</span>
                      <span className="ml-2 text-xs text-[var(--text)] opacity-70">
                        {(npc.interactions ?? []).map((i) => i.name).join(', ')}
                      </span>
                    </div>
                    <button
                      type="button"
                      disabled={associatingId === npc.id}
                      onClick={() => handleAssociate(npc)}
                      className="shrink-0 btn-primary text-xs disabled:opacity-50"
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
                className="btn-primary text-sm"
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
              <div className="mt-5 border-t-[length:var(--border-w)] border-[var(--line)] pt-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-[var(--text)] opacity-70">Ficha</p>
                <NpcSheetPanel
                  campaignId={campaignId}
                  npc={selectedDetail}
                  isMaster={isMaster}
                  onChanged={handleSheetChanged}
                />
              </div>
              {isMaster && (
                <div className="mt-5 flex flex-wrap items-center gap-2 border-t-[length:var(--border-w)] border-[var(--line)] pt-4">
                  <button
                    type="button"
                    onClick={() => setEditing(true)}
                    className="btn-primary text-sm"
                  >
                    Editar
                  </button>
                  <button
                    type="button"
                    onClick={() => toggleVisibility(selected)}
                    className={`btn-primary text-sm ${
                      selected.visible
                        ? 'btn-primary'
                        : 'btn-secondary'
                    }`}
                  >
                    {selected.visible ? 'Visível aos jogadores' : 'Oculto dos jogadores'}
                  </button>
                  {confirmRemove ? (
                    <span className="ml-auto flex items-center gap-2">
                      <span className="text-xs text-[var(--accent)]">Remover da campanha?</span>
                      <button
                        type="button"
                        onClick={() => handleRemove(selected)}
                        disabled={removing}
                        className="btn-primary text-sm disabled:opacity-50"
                      >
                        {removing ? 'Removendo...' : 'Confirmar'}
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmRemove(false)}
                        className="btn-ghost text-sm"
                      >
                        Cancelar
                      </button>
                    </span>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setConfirmRemove(true)}
                      className="ml-auto btn-secondary text-sm text-[var(--accent)]"
                    >
                      Remover
                    </button>
                  )}
                </div>
              )}
            </>
          ) : (
            <p className="py-6 text-center text-sm text-[var(--text)] opacity-70">Carregando...</p>
          )}
        </Modal>
      )}
    </div>
  );
}
