import { useCallback, useEffect, useState } from 'react';
import * as api from '../../api/progression';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { CLASSIFICATIONS, KEIKEN, KEIKEN_FLAVOR, KEIKEN_MIN } from './sheetRules';
import Collapse from '../ui/Collapse';

const KEIKEN_ORDER = Object.keys(KEIKEN);
const PS_TARGETS = [
  ['STR', 'Força'], ['DEX', 'Destreza'], ['CON', 'Constituição'], ['INT', 'Inteligência'], ['CHA', 'Carisma'],
  ['MEN', 'Mental'], ['HP', 'HP máx. (+3)'], ['SP', 'SP máx. (+3)'],
];
const TARGET_LABEL = Object.fromEntries(PS_TARGETS);
const STATUS = { PENDING: 'Aguardando o Mestre', APPROVED: 'Aprovada', REJECTED: 'Recusada' };
const allocText = (a) => Object.entries(a).map(([k, n]) => `${TARGET_LABEL[k] ?? k} ${n}`).join(', ');

/** Rival: lutas e Leitura (0–3); dono/Mestre anotam arte e técnicas conhecidas. */
function RivalRow({ r, canEdit, isMaster, run }) {
  const [editing, setEditing] = useState(false);
  const [art, setArt] = useState(r.art ?? '');
  const [known, setKnown] = useState(r.knownTechniques.join(', '));
  async function save() {
    const knownTechniques = known.split(',').map((x) => x.trim()).filter(Boolean);
    if (await run(api.updateRival, r.id, { art: art.trim() || null, knownTechniques })) setEditing(false);
  }
  return (
    <li className="card-round flex flex-col gap-1 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <strong className="panel-title flex-1 text-base">{r.name}</strong>
        {r.art && <span className="tag">{r.art}</span>}
        <span className="tag">{r.fights} {r.fights === 1 ? 'luta' : 'lutas'}</span>
        <span className="tag-accent tag">Leitura +{r.readBonus}</span>
        {canEdit && !editing && <button type="button" className="btn-ghost px-1 text-xs" onClick={() => setEditing(true)}>Editar</button>}
        {isMaster && <button type="button" className="btn-ghost px-1 text-xs" onClick={() => run(api.removeRival, r.id)}>Remover</button>}
      </div>
      {editing ? (
        <div className="flex flex-col gap-2">
          <input className="field m-0" maxLength={100} placeholder="Arte do rival" value={art} onChange={(e) => setArt(e.target.value)} />
          <input className="field m-0" placeholder="Técnicas conhecidas, separadas por vírgula" value={known} onChange={(e) => setKnown(e.target.value)} />
          <div className="flex gap-2">
            <button type="button" className="btn-secondary px-2 text-xs" onClick={save}>Salvar</button>
            <button type="button" className="btn-ghost px-2 text-xs" onClick={() => setEditing(false)}>Cancelar</button>
          </div>
        </div>
      ) : r.knownTechniques.length > 0 && <p className="text-fine">Técnicas conhecidas: {r.knownTechniques.join(', ')}</p>}
    </li>
  );
}

const small = 'text-fine';

function Bar({ value, max, color }) {
  const pct = max > 0 ? Math.min(100, (100 * value) / max) : 100;
  return (
    <div className="h-2.5 w-full border-2 border-[var(--line)]" role="presentation">
      <div className="h-full" style={{ width: `${pct}%`, background: color }} />
    </div>
  );
}

/** Instinto do catálogo: recolhido mostra o nome; aberto, a descrição e a ação. */
function InstinctCard({ i, chosen, canChoose, isMaster, onChoose, onRemove }) {
  const [open, setOpen] = useState(false);
  return (
    <li className={`card-round flex flex-col gap-1 text-sm ${!chosen && !canChoose ? 'opacity-60' : ''}`}>
      <div className="flex flex-wrap items-center gap-2">
        <button type="button" className="flex flex-1 items-center gap-2 text-left" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
          <strong className="panel-title flex-1 text-base">{i.name}</strong>
          {chosen && <span className="tag-accent tag">Escolhido</span>}
          <span aria-hidden="true" className="text-xs">{open ? '▲' : '▼'}</span>
        </button>
        {canChoose && <button type="button" className="btn-secondary px-2 text-xs" onClick={onChoose}>Escolher</button>}
        {chosen && isMaster && <button type="button" className="btn-ghost px-1 text-xs" onClick={onRemove}>Remover</button>}
      </div>
      {open && i.description && <p className="text-xs">{i.description}</p>}
    </li>
  );
}

/** F4b: à esquerda Keiken, PS e Instintos; à direita Lutas e Rivais. Cada seção recolhe. */
export default function ProgressPanel({ sheetId, progress: p, campaignId, isMaster, isOwner, refreshKey }) {
  const toast = useToast();
  const [data, setData] = useState(null);
  const [catalog, setCatalog] = useState([]);
  const [alloc, setAlloc] = useState({});

  const load = useCallback(async () => {
    try { setData(await api.getProgression(sheetId)); } catch (err) { toast(parseApiError(err).message, 'error'); }
  }, [sheetId, toast]);
  useEffect(() => { load(); }, [load, refreshKey]);
  useEffect(() => {
    let active = true;
    api.listInstincts(campaignId).then((d) => active && setCatalog(d)).catch(() => {});
    return () => { active = false; };
  }, [campaignId]);

  async function run(fn, ...args) {
    try { setData(await fn(sheetId, ...args)); return true; } catch (err) { toast(parseApiError(err).message, 'error'); return false; }
  }

  if (!data || !p) return <div className="skeleton h-16 w-full" />;
  const pending = data.distributions.find((d) => d.status === 'PENDING');
  const used = Object.values(alloc).reduce((a, b) => a + b, 0);
  const free = p.instinctSlots - data.instincts.length;
  const rank = KEIKEN_ORDER.indexOf(p.keiken);
  const next = KEIKEN_ORDER[rank + 1];
  const chosenIds = new Map(data.instincts.map((c) => [c.instinctId, c]));
  const canAct = isOwner || isMaster;

  async function submitAlloc() {
    const body = Object.fromEntries(Object.entries(alloc).filter(([, n]) => n > 0));
    if (await run(api.requestOvercome, body)) { setAlloc({}); toast('Distribuição enviada ao Mestre.'); }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <div className="flex flex-col gap-5">
        <Collapse title="Keiken" aside={<span className="tag" style={{ borderColor: 'var(--keiken)', color: 'var(--keiken)' }}>{KEIKEN[p.keiken]}</span>}>
          <div className="flex flex-col gap-1">
            <div className="flex items-baseline justify-between gap-2">
              <span className={small}>EL rumo a {next ? KEIKEN[next] : 'topo'}</span>
              <span>
                <span className="panel-title text-xl leading-none" style={{ color: 'var(--chip-el)' }}>{p.fightExperience}</span>
                {p.nextKeikenAt != null && <span className="panel-title text-sm opacity-70"> / {p.nextKeikenAt}</span>}
              </span>
            </div>
            <Bar value={p.fightExperience - KEIKEN_MIN[p.keiken]}
              max={p.nextKeikenAt == null ? 0 : p.nextKeikenAt - KEIKEN_MIN[p.keiken]} color="var(--chip-el)" />
          </div>
          <div className="flex flex-col gap-1">
            <div className="flex items-baseline justify-between gap-2">
              <span className={small}>EL de sparring (teto da patente)</span>
              <span className="panel-title text-sm">{p.sparringExperience} / {p.sparringCap}</span>
            </div>
            <Bar value={p.sparringExperience} max={p.sparringCap} color="var(--keiken)" />
          </div>
        </Collapse>

        <Collapse title="Pontos de Superação"
          aside={<span className="panel-title text-xl leading-none" style={{ color: 'var(--chip-ps)' }}>{p.pendingOvercomePoints}</span>}>
          {p.barrierOvercomePoints > 0 && <span className={small}>{p.barrierOvercomePoints} deles cruzam Barreira</span>}
          {pending ? (
            <div className="card-round flex flex-wrap items-center gap-2 text-sm">
              <span className={small}>Pedido</span><strong className="flex-1">{allocText(pending.allocations)}</strong>
              {isMaster ? (
                <>
                  <button type="button" className="btn-primary px-2 text-xs" onClick={() => run(api.reviewOvercome, pending.id, true, null)}>Aprovar</button>
                  <button type="button" className="btn-secondary px-2 text-xs" onClick={() => run(api.reviewOvercome, pending.id, false, null)}>Recusar</button>
                </>
              ) : <span className="tag">Aguardando o Mestre</span>}
            </div>
          ) : p.pendingOvercomePoints === 0 ? (
            <span className={small}>Nenhum PS pendente</span>
          ) : isOwner ? (
            <div className="flex flex-col gap-2">
              <span className={small}>
                {p.pendingOvercomePoints - used} de {p.pendingOvercomePoints} livres · 1 PS = +1 atributo ou +3 de status ·
                INT/CAR máx. 2 · TAL e PQ não recebem
              </span>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                {PS_TARGETS.map(([k, l]) => (
                  <label key={k} className={small}>
                    {l}
                    <input type="number" min={0} max={k === 'INT' || k === 'CHA' ? 2 : p.pendingOvercomePoints}
                      className="field mt-1 w-full px-2 py-1" value={alloc[k] ?? ''}
                      onChange={(e) => setAlloc((a) => ({ ...a, [k]: Math.max(0, Number(e.target.value) || 0) }))} />
                  </label>
                ))}
              </div>
              <button type="button" className="btn-primary self-start" disabled={used === 0 || used > p.pendingOvercomePoints}
                onClick={submitAlloc}>Pedir aprovação</button>
            </div>
          ) : <span className={small}>{p.pendingOvercomePoints} PS pendentes; o jogador distribui</span>}
          {data.distributions.filter((d) => d.status !== 'PENDING').slice(0, 5).map((d) => (
            <span key={d.id} className={`${small} opacity-70`}>{STATUS[d.status]}: {allocText(d.allocations)}{d.reviewNote && ` — ${d.reviewNote}`}</span>
          ))}
        </Collapse>

        <Collapse title="Instintos" aside={<span className={small}>{data.instincts.length}/{p.instinctSlots} vagas</span>}>
          {catalog.length === 0 && <span className={small}>Nenhum Instinto no catálogo</span>}
          {KEIKEN_ORDER.slice(1).map((rk, idx) => {
            const list = catalog.filter((i) => i.rank === rk);
            if (list.length === 0) return null;
            const unlocked = idx + 1 <= rank;
            return (
              <Collapse key={rk} defaultOpen={unlocked} titleClass="text-xs font-bold uppercase tracking-wider"
                title={<>{KEIKEN[rk]} {!unlocked && '· bloqueado'}{KEIKEN_FLAVOR[rk] && <span className="ml-2 font-normal normal-case tracking-normal opacity-70">{KEIKEN_FLAVOR[rk]}</span>}</>}>
                <ul className="flex flex-col gap-2">
                  {list.map((i) => (
                    <InstinctCard key={i.id} i={i} chosen={chosenIds.has(i.id)} isMaster={isMaster}
                      canChoose={canAct && unlocked && free > 0 && !chosenIds.has(i.id)}
                      onChoose={() => run(api.chooseInstinct, i.id)}
                      onRemove={() => run(api.removeInstinct, chosenIds.get(i.id).id)} />
                  ))}
                </ul>
              </Collapse>
            );
          })}
        </Collapse>
      </div>

      <div className="flex flex-col gap-5">
        <Collapse title="Lutas">
          {data.fights.length === 0 ? <span className={small}>Nenhuma luta registrada</span> : (
            <ul className="flex flex-col gap-1 text-sm">
              {data.fights.slice(0, 20).map((f) => (
                <li key={f.id} className="flex flex-wrap items-center gap-2">
                  <span className="tag">{f.dayNumber != null ? `Dia ${f.dayNumber}` : f.fightDate}</span>
                  <strong>{CLASSIFICATIONS[f.classification]}</strong>
                  <span style={{ color: 'var(--chip-el)' }}>+{f.elGained} EL</span>
                  <span style={{ color: 'var(--chip-ps)' }}>+{f.psGained} PS</span>
                  {f.milestones.length > 0 && <span className="text-fine opacity-70">{f.milestones.join(', ')}</span>}
                  {f.note && <span className="text-fine opacity-70">— {f.note}</span>}
                </li>
              ))}
            </ul>
          )}
        </Collapse>

        <Collapse title="Rivais">
          {data.rivals.length === 0 ? <span className={small}>Nenhum rival — o Mestre indica o oponente ao fechar a luta</span> : (
            <ul className="flex flex-col gap-2">
              {data.rivals.map((r) => <RivalRow key={r.id} r={r} canEdit={canAct} isMaster={isMaster} run={run} />)}
            </ul>
          )}
        </Collapse>
      </div>
    </div>
  );
}
