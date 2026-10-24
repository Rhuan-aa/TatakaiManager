import { useCallback, useEffect, useState } from 'react';
import { listModules } from '../../api/catalog';
import * as api from '../../api/notebook';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { BaseEditor, TechniqueEditor } from '../catalog/ArtEditor';
import { GRADS, emptyBase, emptyTechnique, lab } from '../catalog/artLabels';

const MAX_DRAFTS = 3;
const STATUS = { DRAFT: 'Rascunho', PENDING_APPROVAL: 'Aguardando o Mestre', APPROVED: 'Aprovado' };
/** Rascunhos do Caderno usam os tetos de Arte Criada (o servidor confere com a mesma categoria). */
const CATEGORY = 'CREATED';

/** F4c: Caderno Marcial (até 3 rascunhos, editor da Criação Marcial) e Forja (1 em andamento, sessões na Agenda). */
export default function NotebookPanel({ sheetId, isMaster, isOwner, refreshKey }) {
  const toast = useToast();
  const [data, setData] = useState(null);
  const [modules, setModules] = useState([]);
  const [editing, setEditing] = useState(null); // { id?, kind, move }

  const load = useCallback(async () => {
    try { setData(await api.getNotebook(sheetId)); } catch (err) { toast(parseApiError(err).message, 'error'); }
  }, [sheetId, toast]);
  useEffect(() => { load(); }, [load, refreshKey]);
  useEffect(() => {
    let active = true;
    listModules().then((m) => active && setModules(m)).catch(() => {});
    return () => { active = false; };
  }, []);

  async function run(fn, ...args) {
    try { setData(await fn(sheetId, ...args)); return true; } catch (err) { toast(parseApiError(err).message, 'error'); return false; }
  }

  async function save(andSubmit) {
    const body = editing.kind === 'BASE' ? { kind: 'BASE', base: editing.move } : { kind: 'TECHNIQUE', technique: editing.move };
    try {
      let r = editing.id ? await api.updateDraft(sheetId, editing.id, body) : await api.createDraft(sheetId, body);
      const id = editing.id ?? r.drafts[r.drafts.length - 1].id;
      if (andSubmit) r = await api.submitDraft(sheetId, id);
      setData(r);
      setEditing(null);
      toast(andSubmit ? 'Rascunho enviado ao Mestre.' : 'Rascunho salvo.');
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    }
  }

  if (!data) return <div className="skeleton h-16 w-full" />;
  const canAct = isOwner || isMaster;
  const forge = data.forge;

  if (editing) {
    const props = {
      category: CATEGORY,
      onChange: (move) => setEditing((e) => ({ ...e, move })),
      onRemove: () => setEditing(null),
    };
    return (
      <div className="flex flex-col gap-3">
        {editing.kind === 'BASE'
          ? <BaseEditor b={editing.move} modules={modules.filter((m) => m.appliesTo === 'BASE')} {...props} />
          : <TechniqueEditor t={editing.move} modules={modules.filter((m) => m.appliesTo === 'TECHNIQUE')} {...props} />}
        <div className="flex flex-wrap gap-2">
          <button type="button" className="btn-secondary" disabled={!editing.move.name.trim()} onClick={() => save(false)}>Salvar</button>
          {isOwner && (
            <button type="button" className="btn-primary" disabled={!editing.move.name.trim()} onClick={() => save(true)}>
              Salvar e enviar ao Mestre
            </button>
          )}
          <button type="button" className="btn-ghost" onClick={() => setEditing(null)}>Cancelar</button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <section className="flex flex-col gap-2">
        <h4 className="panel-title">Forja</h4>
        {forge ? (
          <div className="flex flex-col gap-1">
            <div className="flex flex-wrap items-baseline gap-2">
              <strong>{forge.name}</strong>
              <span className="panel-title text-2xl leading-none">{forge.accumulatedPoints}</span>
              <span className="panel-title text-base opacity-70">/ {forge.requiredPoints}</span>
              {isMaster && <button type="button" className="btn-ghost px-1 text-xs" onClick={() => run(api.abandonForge)}>Abandonar</button>}
            </div>
            <div className="h-2 w-full border-2 border-[var(--line)]">
              <div className="h-full" style={{ width: `${(100 * forge.accumulatedPoints) / forge.requiredPoints}%`, background: 'var(--stat-complexity)' }} />
            </div>
            <p className="text-fine">Agende a ação solo <strong>Forja</strong> na Agenda: cada sessão rende 1 + metade do mod. de TAL e +2 Desgaste Mental.</p>
          </div>
        ) : <p className="text-fine">Nenhuma Forja em andamento. Aprove um rascunho e inicie a Forja.</p>}
      </section>

      <section className="flex flex-col gap-2">
        <h4 className="panel-title">Caderno Marcial <span className="text-fine">({data.drafts.length}/{MAX_DRAFTS})</span></h4>
        {data.drafts.length === 0 && <p className="text-fine">Nenhum rascunho.</p>}
        <ul className="flex flex-col gap-2">
          {data.drafts.map((d) => {
            const inForge = forge?.draftId === d.id;
            return (
              <li key={d.id} className="card-round flex flex-col gap-1 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <strong className="panel-title flex-1 text-base">{d.name}</strong>
                  <span className="tag">{d.kind === 'BASE' ? 'Base' : 'Técnica'}</span>
                  <span className="tag">{lab(GRADS, d.graduation)}</span>
                  <span className={d.status === 'APPROVED' ? 'tag-accent tag' : 'tag'}>{inForge ? 'Na Forja' : STATUS[d.status]}</span>
                </div>
                <div className="text-fine flex flex-wrap gap-3">
                  <span>Complexidade {d.check.finalComplexity} / teto {d.check.ceiling}</span>
                  <span>Forja: {3 * d.check.finalComplexity} pontos</span>
                  {d.check.issues.length > 0 && <span style={{ color: 'var(--accent)' }}>{d.check.issues.join('; ')}</span>}
                </div>
                {d.reviewNote && <p className="caption self-start">Mestre: {d.reviewNote}</p>}
                <div className="flex flex-wrap gap-2">
                  {canAct && d.status === 'DRAFT' && (
                    <button type="button" className="btn-secondary px-2 text-xs"
                      onClick={() => setEditing({ id: d.id, kind: d.kind, move: d.kind === 'BASE' ? d.base : d.technique })}>Editar</button>
                  )}
                  {isOwner && d.status === 'DRAFT' && (
                    <button type="button" className="btn-primary px-2 text-xs" onClick={() => run(api.submitDraft, d.id)}>Enviar ao Mestre</button>
                  )}
                  {isMaster && d.status === 'PENDING_APPROVAL' && (
                    <>
                      <button type="button" className="btn-primary px-2 text-xs" onClick={() => run(api.reviewDraft, d.id, true, null)}>Aprovar</button>
                      <button type="button" className="btn-secondary px-2 text-xs" onClick={() => run(api.reviewDraft, d.id, false, null)}>Devolver</button>
                    </>
                  )}
                  {canAct && d.status === 'APPROVED' && !forge && (
                    <button type="button" className="btn-primary px-2 text-xs" onClick={() => run(api.startForge, d.id)}>Iniciar Forja</button>
                  )}
                  {canAct && !inForge && (
                    <button type="button" className="btn-ghost px-2 text-xs" onClick={() => run(api.deleteDraft, d.id)}>Apagar</button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
        {canAct && data.drafts.length < MAX_DRAFTS && (
          <div className="flex flex-wrap gap-2">
            <button type="button" className="btn-secondary"
              onClick={() => setEditing({ kind: 'TECHNIQUE', move: { ...emptyTechnique(), graduation: 'GENERIC' } })}>+ Técnica</button>
            <button type="button" className="btn-secondary"
              onClick={() => setEditing({ kind: 'BASE', move: { ...emptyBase(), graduation: 'GENERIC' } })}>+ Base</button>
          </div>
        )}
      </section>
    </div>
  );
}
