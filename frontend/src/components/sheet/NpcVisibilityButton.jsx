import { useRef, useState } from 'react';
import { getNpcSheetVisibility, updateNpcSheetVisibility } from '../../api/characterSheets';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import Icon from '../ui/Icon';

// Seções da ficha de NPC liberáveis aos jogadores (espelha SheetSection.NPC_SECTIONS)
const SECTIONS = [
  ['HP', 'HP'], ['SP', 'SP'], ['KP', 'PQ'], ['FATIGUE', 'Cansaço'], ['ATTRIBUTES', 'Atributos'], ['TRAITS', 'Traços'],
  ['CLANS', 'Clãs'], ['INJURIES', 'Ferimentos'], ['INTERACTIONS', 'Interações'], ['INVENTORY', 'Itens'], ['ARTS', 'Artes'],
  ['BASES', 'Bases'], ['TECHNIQUES', 'Técnicas'], ['RADAR', 'Gráfico de limitadores'], ['BODY_MAP', 'Boneco'], ['IMAGE', 'Imagem'],
].map(([section, label]) => ({ section, label }));

/** Botão "Visibilidade" (Mestre dono do NPC): pop-up com as 16 seções, salvas num PUT em lote. */
export default function NpcVisibilityButton({ campaignId, npcId }) {
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
