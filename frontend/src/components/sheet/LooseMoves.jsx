import { useEffect, useState } from 'react';
import { listModules } from '../../api/catalog';
import { createLooseMove, deleteLooseMove, listLooseMoves, setLooseVisible } from '../../api/looseMoves';
import { listSheets } from '../../api/characterSheets';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { BaseEditor, TechniqueEditor } from '../catalog/ArtEditor';
import { GRADS, emptyBase, emptyTechnique, lab } from '../catalog/artLabels';

/** Montagem na hora (Criação Marcial, categoria Criada): devolve { kind, name, technique|base } em onSave. */
export function MoveBuilder({ submitLabel, onSave, onCancel }) {
  const [modules, setModules] = useState([]);
  const [kind, setKind] = useState('TECHNIQUE');
  const [move, setMove] = useState(emptyTechnique);
  useEffect(() => {
    let active = true;
    listModules().then((m) => active && setModules(m)).catch(() => {});
    return () => { active = false; };
  }, []);
  const pickKind = (k) => { setKind(k); setMove(k === 'BASE' ? emptyBase() : emptyTechnique()); };
  const props = { category: 'CREATED', onChange: setMove, onRemove: onCancel };
  return (
    <div className="flex flex-col gap-3">
      <select className="field m-0 w-auto" aria-label="Tipo" value={kind} onChange={(e) => pickKind(e.target.value)}>
        <option value="TECHNIQUE">Técnica</option><option value="BASE">Base</option>
      </select>
      {kind === 'BASE'
        ? <BaseEditor b={move} modules={modules.filter((m) => m.appliesTo === 'BASE')} {...props} />
        : <TechniqueEditor t={move} modules={modules.filter((m) => m.appliesTo === 'TECHNIQUE')} {...props} />}
      <div className="flex gap-2">
        <button type="button" className="btn-primary" disabled={!move.name.trim()}
          onClick={() => onSave({ kind, name: move.name.trim(), ...(kind === 'BASE' ? { base: move } : { technique: move }) })}>{submitLabel}</button>
        <button type="button" className="btn-ghost" onClick={onCancel}>Cancelar</button>
      </div>
    </div>
  );
}

/** Catálogo de avulsas da campanha: o Mestre monta e decide a visibilidade; quem pode adicionar escolhe daqui. */
export function LooseCatalog({ campaignId, isMaster, canPick, onPick }) {
  const toast = useToast();
  const [items, setItems] = useState(null);
  const [building, setBuilding] = useState(false);
  const fail = (err) => toast(parseApiError(err).message, 'error');
  const load = () => listLooseMoves(campaignId).then(setItems).catch(fail);
  useEffect(() => { load(); }, [campaignId]); // eslint-disable-line react-hooks/exhaustive-deps

  async function create(body) {
    try { await createLooseMove(campaignId, { ...body, visible: false }); setBuilding(false); load(); } catch (err) { fail(err); }
  }
  if (building) return <MoveBuilder submitLabel="Salvar no catálogo" onSave={create} onCancel={() => setBuilding(false)} />;
  if (!items) return <div className="skeleton h-10 w-full" />;
  return (
    <div className="flex flex-col gap-2">
      {items.length === 0 && <p className="text-fine">Nenhuma técnica ou base avulsa no catálogo{isMaster ? '.' : ' liberada pelo Mestre.'}</p>}
      <ul className="flex flex-col gap-2">
        {items.map((m) => (
          <li key={m.id} className="card-round flex flex-wrap items-center gap-2 text-sm">
            <strong className="panel-title flex-1 text-base">{m.name}</strong>
            <span className="tag">{m.kind === 'BASE' ? 'Base' : 'Técnica'}</span>
            <span className="tag">{lab(GRADS, m.graduation)}</span>
            <span className="tag">Complexidade {m.complexity}</span>
            {isMaster && (
              <label className="flex items-center gap-1 text-xs">
                <input type="checkbox" checked={m.visible}
                  onChange={(e) => setLooseVisible(campaignId, m.id, e.target.checked).then(load).catch(fail)} />
                Visível aos jogadores
              </label>
            )}
            {canPick && <button type="button" className="btn-secondary px-2 text-xs" onClick={() => onPick(m)}>{isMaster ? 'Adicionar' : 'Pedir'}</button>}
            {isMaster && <button type="button" className="btn-ghost px-2 text-xs" onClick={() => deleteLooseMove(campaignId, m.id).then(load).catch(fail)}>Apagar</button>}
          </li>
        ))}
      </ul>
      {isMaster && <button type="button" className="btn-secondary self-start" onClick={() => setBuilding(true)}>Nova no catálogo</button>}
    </div>
  );
}

/** Escolhe a ficha de outro personagem da campanha para ensinar a técnica/base avulsa. */
export function TeachPicker({ campaignId, sheetId, onTeach }) {
  const [sheets, setSheets] = useState([]);
  const [target, setTarget] = useState('');
  useEffect(() => {
    let active = true;
    listSheets(campaignId).then((s) => active && setSheets(s.filter((x) => x.id !== sheetId && !x.npcId))).catch(() => {});
    return () => { active = false; };
  }, [campaignId, sheetId]);
  return (
    <span className="flex flex-wrap items-center gap-1">
      <select className="field m-0 w-auto py-0.5 text-xs" aria-label="Ensinar para" value={target} onChange={(e) => setTarget(e.target.value)}>
        <option value="">Ensinar para…</option>
        {sheets.map((s) => <option key={s.id} value={s.id}>{s.name || 'Sem nome'}</option>)}
      </select>
      <button type="button" className="btn-secondary px-2 text-xs" disabled={!target} onClick={() => { onTeach(target); setTarget(''); }}>Ensinar</button>
    </span>
  );
}
