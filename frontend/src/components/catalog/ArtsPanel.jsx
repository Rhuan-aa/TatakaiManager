import { useCallback, useEffect, useMemo, useState } from 'react';
import { listArts, getArt, deleteArt, reviewArt, submitArt } from '../../api/catalog';
import { parseApiError } from '../../api/parseApiError';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../contexts/ToastContext';
import RichText from '../ui/RichText';
import ArtEditor from './ArtEditor';
import { ART_CATEGORIES, ART_STATUS, GRADS, lab } from './artLabels';

function Rules({ rules }) {
  if (!rules?.length) return null;
  return (
    <ul className="text-sm list-disc pl-5">
      {rules.map((r, i) => <li key={i}><strong>{r.name}:</strong> <RichText text={r.text} className="inline-block align-top" /></li>)}
    </ul>
  );
}

const STAT_COLORS = {
  'Função': 'var(--stat-function)', Complexidade: 'var(--stat-complexity)', 'Pontos de Treino': 'var(--stat-training)',
  'Gasto de SP': 'var(--stat-cost)', Alcance: 'var(--stat-range)',
};

function Chip({ label, value }) {
  if (value == null || value === '') return null;
  const color = STAT_COLORS[label];
  return <span className="tag" style={color ? { borderColor: color, color } : undefined}>{label}:{' '}{value}</span>;
}

function Assembly({ text }) {
  const [show, setShow] = useState(false);
  return (
    <div className="flex flex-col gap-1">
      <button type="button" className="btn-secondary self-start" aria-expanded={show} onClick={() => setShow((s) => !s)}>
        {show ? 'Ocultar montagem' : 'Ver montagem'}
      </button>
      {show && (
        <code className="tag text-xs whitespace-pre-wrap" style={{ borderColor: 'var(--stat-assembly)', color: 'var(--stat-assembly)' }}>
          Montagem:{' '}{text}
        </code>
      )}
    </div>
  );
}

// Números calculados pela montagem estruturada; destaca quando divergem do livro.
function CheckLine({ check, m, technique }) {
  if (!check || (!m.variations?.length && !m.items?.length)) return null;
  const diff = m.complexity != null && m.complexity !== check.finalComplexity;
  return (
    <div className="flex flex-col gap-1 text-xs">
      <span className="tag self-start" style={diff ? { borderColor: 'var(--accent)', color: 'var(--accent)' } : undefined}
        title={diff ? 'Diferente da Complexidade do livro' : undefined}>
        Montagem: C {check.rawComplexity}/{check.finalComplexity} · teto {check.ceiling}
        {technique && check.spCosts.length > 0 && ` · SP ${check.spCosts.join('/')}`} · PT {check.trainingPoints}
        {diff && ` (livro: ${m.complexity})`}
      </span>
      {check.issues.length > 0 && (
        <ul className="list-disc pl-5" style={{ color: 'var(--accent)' }}>{check.issues.map((i) => <li key={i}>{i}</li>)}</ul>
      )}
    </div>
  );
}

function MoveCard({ m, technique }) {
  const sp = m.spCost || m.spCostBase;
  return (
    <li className="panel p-3 flex flex-col gap-2">
      <strong className="panel-title">{m.name}</strong>
      <RichText text={m.description} className="text-sm" />
      <Rules rules={m.rules} />
      {m.effect && <div className="text-sm"><strong>Efeito:</strong><RichText text={m.effect} /></div>}
      {m.restrictions && <div className="text-sm"><strong>Restrições:</strong><RichText text={m.restrictions} /></div>}
      <div className="flex flex-wrap gap-2">
        {technique ? (
          <>
            <Chip label="Alcance" value={m.range} />
            <Chip label="Lado" value={m.side} />
          </>
        ) : <Chip label="Função" value={m.function} />}
        <Chip label="Complexidade" value={m.complexity} />
        <Chip label="Pontos de Treino" value={m.trainingPoints} />
        {technique && <Chip label="Gasto de SP" value={sp} />}
      </div>
      <CheckLine check={m.check} m={m} technique={technique} />
      {m.assembly && <Assembly text={m.assembly} />}
    </li>
  );
}

function Group({ title, items, technique }) {
  if (!items?.length) return null;
  return (
    <section className="flex flex-col gap-3" aria-label={title}>
      <h4 className="panel-title">{title}</h4>
      {GRADS.map((g) => {
        const list = items.filter((i) => (i.graduation ?? 'GENERIC') === g.value);
        if (!list.length) return null;
        return (
          <div key={g.value} className="flex flex-col gap-2">
            <h5 className="caption self-start">{g.label}</h5>
            <ul className="page-gutter grid grid-cols-1 lg:grid-cols-2">
              {list.map((m, i) => <MoveCard key={`${m.name}-${i}`} m={m} technique={technique} />)}
            </ul>
          </div>
        );
      })}
    </section>
  );
}

function ReviewBox({ art, onDone }) {
  const toast = useToast();
  const [reason, setReason] = useState('');
  async function decide(approved) {
    try {
      onDone(await reviewArt(art.id, { approved, reason: reason.trim() || null }));
      toast(approved ? 'Arte aprovada.' : 'Arte devolvida ao autor.', 'success');
    } catch (err) { toast(parseApiError(err).message, 'error'); }
  }
  return (
    <div className="panel flex flex-col gap-2 p-3">
      <h4 className="panel-title">Aprovação</h4>
      <label className="text-xs uppercase">Motivo (opcional)
        <input className="field" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <div className="flex gap-2">
        <button type="button" className="btn-primary" onClick={() => decide(true)}>Aprovar</button>
        <button type="button" className="btn-secondary" onClick={() => decide(false)}>Devolver</button>
      </div>
    </div>
  );
}

function ArtDetail({ art, canDelete, canEdit, ownDraft, canReview, onDelete, onBack, onEdit, onChanged }) {
  const toast = useToast();
  async function submit() {
    try {
      onChanged(await submitArt(art.id));
      toast('Arte enviada para aprovação.', 'success');
    } catch (err) { toast(parseApiError(err).message, 'error'); }
  }
  const techniques = art.techniques?.map((t, i) => ({ ...t, check: art.techniqueChecks?.[i] }));
  const bases = art.bases?.map((b, i) => ({ ...b, check: art.baseChecks?.[i] }));
  return (
    <article className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-2">
        <button className="btn-secondary" onClick={onBack}>Voltar</button>
        {canEdit && <button className="btn-secondary" onClick={onEdit}>Editar</button>}
        {ownDraft && <button className="btn-primary" onClick={submit}>Enviar para aprovação</button>}
        {canDelete && <button className="btn-ghost ml-auto" onClick={onDelete} aria-label={`Excluir ${art.name}`}>Excluir</button>}
      </div>
      {art.reviewNote && <p className="caption self-start">Mestre: {art.reviewNote}</p>}
      {canReview && <ReviewBox art={art} onDone={onChanged} />}
      <header className="panel p-4 flex flex-col gap-2">
        <h3 className="panel-title text-2xl">{art.name}</h3>
        <div className="flex flex-wrap gap-2">
          <span className="tag-accent">{lab(ART_CATEGORIES, art.category)}</span>
          {art.status && art.status !== 'APPROVED' && <span className="tag">{ART_STATUS[art.status]}</span>}
          {art.martialType && <span className="tag">{art.martialType}</span>}
          {art.rank && <span className="tag">Rank {art.rank}</span>}
          <span className="tag">{art.campaignId ? 'Campanha' : 'Sistema'}</span>
          <span className="tag">v{art.version}</span>
        </div>
        <RichText text={art.description} className="text-sm" />
        {art.attackSpecializations?.length > 0 && <p className="text-sm"><strong>Especializações de ataque:</strong> {art.attackSpecializations.join(', ')}</p>}
        {art.reactionSpecializations?.length > 0 && <p className="text-sm"><strong>Especializações de reação:</strong> {art.reactionSpecializations.join(', ')}</p>}
        {art.requirement && <div className="text-sm"><strong>Requisito:</strong><RichText text={art.requirement} /></div>}
      </header>
      {art.sections?.map((s, i) => (
        <section key={i} className="panel p-3 flex flex-col gap-2">
          <h4 className="caption self-start">{s.name}</h4>
          <RichText text={s.text} className="text-sm" />
        </section>
      ))}
      {art.passives?.length > 0 && (
        <section className="flex flex-col gap-2" aria-label="Passivas">
          <h4 className="panel-title">Passivas</h4>
          <ul className="page-gutter flex flex-col">
            {art.passives.map((p, i) => (
              <li key={i} className="panel p-3 flex flex-col gap-1">
                <strong>{p.name}</strong>
                {p.group && <span className="tag self-start">{p.group}</span>}
                <RichText text={p.description} className="text-sm" />
              </li>
            ))}
          </ul>
        </section>
      )}
      <Group title="Bases" items={bases} />
      <Group title="Técnicas" items={techniques} technique />
    </article>
  );
}

export default function ArtsPanel({ campaignId, isMaster, globalEditor, refreshKey }) {
  const toast = useToast();
  const { user } = useAuth();
  const [editing, setEditing] = useState(null); // {} = nova arte; objeto = arte em edição
  const [arts, setArts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState('');
  const [type, setType] = useState('');
  const [open, setOpen] = useState(null);

  const load = useCallback(async () => {
    try { setArts(await listArts(campaignId)); } catch (err) { toast(parseApiError(err).message, 'error'); } finally { setLoading(false); }
  }, [campaignId, toast]);
  useEffect(() => { load(); }, [load, refreshKey]);

  const types = useMemo(() => [...new Set(arts.map((a) => a.martialType).filter(Boolean))].sort(), [arts]);
  const items = useMemo(() => {
    const term = q.trim().toLowerCase();
    return arts.filter((a) => (!term || a.name.toLowerCase().includes(term)) && (!type || a.martialType === type))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [arts, q, type]);
  const canWrite = (a) => (a.campaignId ? isMaster : globalEditor);
  const ownDraft = (a) => a.status === 'DRAFT' && a.authorId === user?.userId;
  const canDelete = (a) => canWrite(a) || ownDraft(a);
  const canCreate = campaignId ? true : globalEditor;

  async function show(id) {
    try { setOpen(await getArt(id)); } catch (err) { toast(parseApiError(err).message, 'error'); }
  }
  async function remove(a) {
    if (!window.confirm(`Excluir "${a.name}"?`)) return;
    try {
      await deleteArt(a.id);
      toast('Arte excluída.', 'success');
      setOpen(null);
      load();
    } catch (err) { toast(parseApiError(err).message, 'error'); }
  }

  if (editing) {
    return (
      <ArtEditor campaignId={campaignId} art={editing.id ? editing : null}
        canChooseCategory={campaignId ? isMaster : globalEditor}
        onSaved={(a) => { setEditing(null); setOpen(a); load(); }} onCancel={() => setEditing(null)} />
    );
  }

  if (open) {
    return (
      <ArtDetail art={open} canDelete={canDelete(open)} canEdit={canWrite(open) || ownDraft(open)} ownDraft={ownDraft(open)}
        canReview={canWrite(open) && open.status === 'PENDING_APPROVAL'}
        onDelete={() => remove(open)} onBack={() => setOpen(null)} onEdit={() => setEditing(open)}
        onChanged={(a) => { setOpen(a); load(); }} />
    );
  }

  return (
    <>
      <div className="panel p-3 flex flex-wrap gap-3 items-end" role="search">
        <label className="flex flex-col text-xs uppercase gap-1">Buscar
          <input className="field" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Nome…" />
        </label>
        <label className="flex flex-col text-xs uppercase gap-1">Tipo marcial
          <select className="field" value={type} onChange={(e) => setType(e.target.value)}>
            <option value="">Todos</option>
            {types.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </label>
        {canCreate && <button type="button" className="btn-primary ml-auto" onClick={() => setEditing({})}>+ Nova arte (Criação Marcial)</button>}
      </div>
      {loading ? <div className="skeleton h-24" aria-busy="true" />
        : items.length === 0 ? <p className="caption">Nenhuma arte encontrada.</p> : (
          <ul className="page-gutter flex flex-col">
            {items.map((a) => (
              <li key={a.id} className="panel p-3 flex flex-col gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  <strong className="panel-title">{a.name}</strong>
                  <span className="tag-accent">{lab(ART_CATEGORIES, a.category)}</span>
                  {a.status && a.status !== 'APPROVED' && <span className="tag">{ART_STATUS[a.status]}</span>}
                  {a.martialType && <span className="tag">{a.martialType}</span>}
                  <span className="tag">{a.campaignId ? 'Campanha' : 'Sistema'}</span>
                  <span className="tag">{a.bases} base(s)</span>
                  <span className="tag">{a.techniques} técnica(s)</span>
                  <span className="ml-auto flex gap-2">
                    <button className="btn-secondary" onClick={() => show(a.id)} aria-label={`Abrir ${a.name}`}>Abrir</button>
                    {canDelete(a) && <button className="btn-ghost" onClick={() => remove(a)} aria-label={`Excluir ${a.name}`}>Excluir</button>}
                  </span>
                </div>
                {a.attackSpecializations?.length > 0 && <p className="text-sm"><strong>Ataque:</strong> {a.attackSpecializations.join(', ')}</p>}
                {a.reactionSpecializations?.length > 0 && <p className="text-sm"><strong>Reação:</strong> {a.reactionSpecializations.join(', ')}</p>}
              </li>
            ))}
          </ul>
        )}
    </>
  );
}
