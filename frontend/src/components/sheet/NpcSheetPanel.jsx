import { useCallback, useEffect, useRef, useState } from 'react';
import {
  getNpcSheet, getCampaignNpcSheet, getNpcSheetVisibility, updateNpcSheetVisibility,
} from '../../api/characterSheets';
import Icon from '../ui/Icon';
import { parseApiError } from '../../api/parseApiError';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../contexts/ToastContext';
import { useCampaignTopic } from '../../hooks/useWebSocket';
import CharacterSheetForm from './CharacterSheetForm';
import CharacterSheetView from './CharacterSheetView';
import MartialPanel from './MartialPanel';

// Seções da ficha de NPC liberáveis aos jogadores (espelha SheetSection.NPC_SECTIONS)
const SECTIONS = [
  ['HP', 'HP'], ['SP', 'SP'], ['KP', 'PQ'], ['FATIGUE', 'Cansaço'], ['ATTRIBUTES', 'Atributos'], ['TRAITS', 'Traços'],
  ['CLANS', 'Clãs'], ['INJURIES', 'Ferimentos'], ['INTERACTIONS', 'Interações'], ['INVENTORY', 'Itens'], ['ARTS', 'Artes'],
  ['BASES', 'Bases'], ['TECHNIQUES', 'Técnicas'], ['RADAR', 'Gráfico de limitadores'], ['BODY_MAP', 'Boneco'], ['IMAGE', 'Imagem'],
].map(([section, label]) => ({ section, label }));

// Ficha leve: os atributos crus (se houver) pré-preenchem o formulário de ficha completa.
const PREFILL = (a) => (a ? {
  str: a.STR || 10, dex: a.DEX || 10, con: a.CON || 10, intl: a.INT || 10, men: a.MEN || 10, tal: a.TAL || 10,
  cha: a.CHA || '',
} : { cha: '' });

/** Botão "Visibilidade" (Mestre dono do NPC): pop-up com as 16 seções, salvas num PUT em lote. */
function VisibilityButton({ campaignId, npcId }) {
  const toast = useToast();
  const ref = useRef(null);
  const [draft, setDraft] = useState(null);
  const [saving, setSaving] = useState(false);

  async function open() {
    ref.current.showModal();
    try {
      const list = await getNpcSheetVisibility(campaignId, npcId);
      setDraft(Object.fromEntries(SECTIONS.map((s) => [s.section, !!list.find((x) => x.section === s.section)?.visible])));
    } catch (err) {
      toast(parseApiError(err).message, 'error');
      ref.current?.close();
    }
  }

  function close() {
    ref.current.close();
    setDraft(null);
  }

  async function save(e) {
    e.preventDefault();
    setSaving(true);
    try {
      await updateNpcSheetVisibility(campaignId, npcId, draft);
      toast('Visibilidade salva.');
      close();
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setSaving(false);
    }
  }

  const all = (v) => setDraft(Object.fromEntries(SECTIONS.map((s) => [s.section, v])));

  return (
    <>
      <button type="button" className="btn-secondary self-start" onClick={open}>
        <Icon name="lock" size={16} /> Visibilidade
      </button>
      {/* <dialog> nativo: fica na camada de topo, prende o foco; Esc não fecha o modal do NPC por trás */}
      <dialog ref={ref} className="panel m-auto w-[min(560px,calc(100vw-32px))] p-0 text-[var(--text)] backdrop:bg-black/70"
        onCancel={(e) => { e.preventDefault(); close(); }}
        onKeyDown={(e) => e.key === 'Escape' && e.stopPropagation()}>
        <form onSubmit={save} className="flex flex-col gap-3 p-4">
          <h3 className="panel-title text-xl">Visível para os jogadores</h3>
          <p className="hint">Vale só nesta campanha. Seção desmarcada some da ficha do jogador.</p>
          {!draft ? <div className="skeleton h-40 w-full" /> : (
            <>
              <div className="flex gap-2">
                <button type="button" className="btn-ghost text-sm" onClick={() => all(true)}>Marcar tudo</button>
                <button type="button" className="btn-ghost text-sm" onClick={() => all(false)}>Ocultar tudo</button>
              </div>
              <div className="grid gap-2 sm:grid-cols-2">
                {SECTIONS.map((s) => (
                  <label key={s.section} className="flex min-h-[36px] items-center gap-2 text-sm">
                    <input type="checkbox" checked={draft[s.section]}
                      onChange={(e) => setDraft((p) => ({ ...p, [s.section]: e.target.checked }))} />
                    {s.label}
                  </label>
                ))}
              </div>
            </>
          )}
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-ghost" onClick={close}>Cancelar</button>
            <button type="submit" className="btn-primary" disabled={!draft || saving}>{saving ? 'Salvando...' : 'Salvar'}</button>
          </div>
        </form>
      </dialog>
    </>
  );
}

export default function NpcSheetPanel({ campaignId, npc, isMaster, onChanged }) {
  const { user } = useAuth();
  const toast = useToast();
  const isOwner = npc.ownerId === user?.userId;
  const [sheet, setSheet] = useState(null);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let active = true;
    (isOwner ? getNpcSheet(npc.id) : getCampaignNpcSheet(campaignId, npc.id))
      .then((s) => active && setSheet(s))
      .catch((err) => {
        if (!active) return;
        setSheet(null);
        if (err?.response?.status !== 404) toast(parseApiError(err).message, 'error');
      })
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [campaignId, npc.id, isOwner, tick, toast]);

  const sheetId = sheet?.id;
  const onSheetEvent = useCallback((msg) => {
    if (sheetId && msg?.sheetId === sheetId) setTick((t) => t + 1);
  }, [sheetId]);
  useCampaignTopic(campaignId, 'sheets', onSheetEvent);

  function changed(s) {
    setSheet(s);
    onChanged?.(s);
  }

  if (loading) return <div className="skeleton h-24 w-full" />;

  if (creating) {
    return (
      <div className="panel p-4">
        <h3 className="panel-title mb-3">Criar ficha</h3>
        <CharacterSheetForm
          npcId={npc.id}
          initialValues={{ name: npc.name, ...PREFILL(sheet?.attributes) }}
          onSaved={(s) => { setCreating(false); changed(s); }}
          onCancel={() => setCreating(false)}
        />
      </div>
    );
  }

  if (sheet && !sheet.combatReady) {
    return (
      <div className="flex flex-col gap-4">
        <div className="panel p-4">
          <p className="caption">Ficha sem atributos e recursos</p>
          <p className="mt-2 text-sm">Este NPC ainda não pode entrar em combate nem rolar. Complete atributos e recursos para liberar.</p>
          {isOwner && <button type="button" className="btn-primary mt-3" onClick={() => setCreating(true)}>Completar ficha</button>}
        </div>
        {sheet.traits?.length > 0 && (
          <div className="panel p-4">
            <h3 className="panel-title mb-3">Traços</h3>
            <ul className="flex flex-col gap-2">
              {sheet.traits.map((t) => (
                <li key={t.id} className="bubble p-2 text-sm"><strong className="panel-title">{t.name}</strong>{t.description && <p className="mt-1 opacity-80">{t.description}</p>}</li>
              ))}
            </ul>
          </div>
        )}
        <MartialPanel sheetId={sheet.id} campaignId={campaignId} isMaster={isMaster} isOwner={isOwner} refreshKey={tick} />
        {isOwner && isMaster && <VisibilityButton campaignId={campaignId} npcId={npc.id} />}
      </div>
    );
  }

  if (!sheet) {
    return isOwner ? (
      <button type="button" className="btn-primary" onClick={() => setCreating(true)}>Criar ficha</button>
    ) : (
      <p className="caption inline-block">Nenhuma informação da ficha disponível.</p>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {isOwner && isMaster && <VisibilityButton campaignId={campaignId} npcId={npc.id} />}
      {/* dono: interações vêm do NPC; jogador: do servidor (null se INTERACTIONS oculta) */}
      <CharacterSheetView sheet={sheet} isMaster={isMaster} campaignId={campaignId} onChange={changed}
        interactions={isOwner ? npc.interactions : sheet.interactions} />
    </div>
  );
}
