import { useCallback, useEffect, useState } from 'react';
import { getArt, listArts } from '../../api/catalog';
import * as api from '../../api/characterTechniques';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { ART_CATEGORIES, GRADS, lab } from '../catalog/artLabels';

const STATES = { REQUESTED: 'Pedida', IN_TRAINING: 'Em treino', ACTIVE: 'Ativa', STORED: 'Guardada' };
const MASTERY = [
  { value: 'SKETCH', label: 'Esboço' }, { value: 'UNCORRECTED', label: 'Não corrigida' },
  { value: 'STANDARD', label: 'Padrão' }, { value: 'IMPROVED', label: 'Melhorada' },
  { value: 'SPECIALIZED', label: 'Especializada' }, { value: 'MASTERED', label: 'Masterizada' },
];
const BONUSES = {
  TECHNIQUE: [['DAMAGE', 'Dano (+2 dano)'], ['HIT', 'Acerto (+1)'], ['COST', 'Gasto (−2 SP)'], ['IMPACT', 'Impacto (+2 no SP)'],
    ['POWER', 'Potência (+1 PQ)'], ['JOKER', 'Coringa'], ['REFINE', 'Refino'], ['TRACTION', 'Tração']],
  BASE: [['BONUS', 'Bônus (+2)'], ['JOKER', 'Coringa'], ['REFINE', 'Refino'], ['TRACTION', 'Tração'], ['REDUCTION', 'Redução (−2)']],
};
const BONUS_LABEL = { ...Object.fromEntries([...BONUSES.TECHNIQUE, ...BONUSES.BASE]), APEX: 'Ápice' };
const GRAD_ORDER = ['GENERIC', 'BEGINNER', 'INTERMEDIATE', 'MASTER'];

function NumberAction({ label, onSubmit, min }) {
  const [v, setV] = useState('');
  return (
    <span className="flex items-center gap-1">
      <input type="number" min={min} className="field m-0 w-16 px-1 py-0.5 text-xs" aria-label={label} value={v}
        onChange={(e) => setV(e.target.value)} />
      <button type="button" className="btn-ghost px-1 text-xs" disabled={v === ''}
        onClick={() => { onSubmit(Number(v)); setV(''); }}>{label}</button>
    </span>
  );
}

function MoveRow({ t, isMaster, canAct, run }) {
  const [bonus, setBonus] = useState('');
  const learned = t.state === 'ACTIVE' || t.state === 'STORED';
  const canLevel = learned && t.nextLevelCost != null && t.masteryPoints >= t.nextLevelCost;
  return (
    <li className="bubble flex flex-col gap-1 p-2 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <strong className="panel-title">{t.name}</strong>
        <span className="tag">{t.kind === 'BASE' ? 'Base' : 'Técnica'}</span>
        <span className="tag">{lab(GRADS, t.graduation)}</span>
        <span className={t.state === 'ACTIVE' ? 'tag-accent tag' : 'tag'}>{STATES[t.state]}</span>
        {learned && <span className="tag" style={{ borderColor: 'var(--stat-complexity)', color: 'var(--stat-complexity)' }}>{lab(MASTERY, t.mastery)}</span>}
        {t.bonuses.map((b, i) => <span key={i} className="tag" style={{ borderColor: 'var(--chip-el)', color: 'var(--chip-el)' }}>{BONUS_LABEL[b] ?? b}</span>)}
      </div>
      <div className="flex flex-wrap items-center gap-3 text-xs">
        <span>C {t.complexity} · PT {t.trainingPoints}</span>
        {t.state === 'IN_TRAINING' && <span>Treino: <strong>{t.trainingProgress}</strong> / {t.trainingPoints}</span>}
        {learned && (
          <span>Maestria: <strong>{t.masteryPoints}</strong>{t.nextLevelCost != null && ` / ${t.nextLevelCost} para o próximo nível`}</span>
        )}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        {isMaster && t.state === 'REQUESTED' && (
          <>
            <button type="button" className="btn-primary px-2 text-xs" onClick={() => run(api.reviewTechnique, t.id, true)}>Aprovar</button>
            <button type="button" className="btn-secondary px-2 text-xs" onClick={() => run(api.reviewTechnique, t.id, false)}>Recusar</button>
          </>
        )}
        {isMaster && t.state === 'IN_TRAINING' && <NumberAction label="+ treino" min={1} onSubmit={(n) => run(api.trainTechnique, t.id, n)} />}
        {isMaster && learned && <NumberAction label="± maestria" onSubmit={(n) => run(api.addMasteryPoints, t.id, n)} />}
        {canAct && learned && (
          <button type="button" className="btn-secondary px-2 text-xs"
            onClick={() => run(api.setTechniqueState, t.id, t.state === 'ACTIVE' ? 'STORED' : 'ACTIVE')}>
            {t.state === 'ACTIVE' ? 'Guardar' : 'Ativar'}
          </button>
        )}
        {canAct && canLevel && (
          <span className="flex items-center gap-1">
            <select className="field m-0 py-0.5 text-xs" aria-label="Bonificação" value={bonus} onChange={(e) => setBonus(e.target.value)}>
              <option value="">Bonificação…</option>
              {BONUSES[t.kind].map(([k, l]) => <option key={k} value={k}>{l}</option>)}
            </select>
            <button type="button" className="btn-primary px-2 text-xs" disabled={!bonus}
              onClick={() => { run(api.levelUp, t.id, bonus); setBonus(''); }}>Subir nível</button>
          </span>
        )}
        {isMaster && (
          <select className="field m-0 py-0.5 text-xs" aria-label={`Degrau de maestria de ${t.name}`} value={t.mastery}
            onChange={(e) => run(api.setMastery, t.id, e.target.value)}>
            {MASTERY.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
        )}
        {isMaster && <button type="button" className="btn-ghost px-2 text-xs" onClick={() => run(api.removeTechnique, t.id)}>Remover</button>}
      </div>
    </li>
  );
}

function ArtCard({ ca, moves, isMaster, canAct, run }) {
  const toast = useToast();
  const [catalogArt, setCatalogArt] = useState(null);
  const [choice, setChoice] = useState('');
  const owned = new Set(moves.map((m) => `${m.kind}:${m.name}`));
  const options = catalogArt ? [
    ...(catalogArt.bases ?? []).map((b) => ({ kind: 'BASE', name: b.name, graduation: b.graduation ?? 'GENERIC' })),
    ...(catalogArt.techniques ?? []).map((t) => ({ kind: 'TECHNIQUE', name: t.name, graduation: t.graduation ?? 'GENERIC' })),
  ].filter((o) => !owned.has(`${o.kind}:${o.name}`) && GRAD_ORDER.indexOf(o.graduation) <= GRAD_ORDER.indexOf(ca.graduation)) : [];

  async function loadMoves() {
    if (catalogArt) return;
    try { setCatalogArt(await getArt(ca.artId)); } catch (err) { toast(parseApiError(err).message, 'error'); }
  }

  return (
    <section className="panel flex flex-col gap-2 p-3">
      <div className="flex flex-wrap items-center gap-2">
        <h4 className="panel-title flex-1">{ca.artName}</h4>
        <span className="tag">{lab(ART_CATEGORIES, ca.category)}</span>
        {isMaster ? (
          <select className="field m-0 w-auto py-0.5 text-xs" aria-label={`Graduação em ${ca.artName}`} value={ca.graduation}
            onChange={(e) => run(api.setArtGraduation, ca.id, e.target.value)}>
            {GRADS.filter((g) => g.value !== 'GENERIC').map((g) => <option key={g.value} value={g.value}>{g.label}</option>)}
          </select>
        ) : <span className="tag">{lab(GRADS, ca.graduation)}</span>}
        {ca.status === 'REQUESTED' && <span className="tag-accent tag">Pedida</span>}
        {isMaster && ca.status === 'REQUESTED' && (
          <>
            <button type="button" className="btn-primary px-2 text-xs" onClick={() => run(api.reviewCharacterArt, ca.id, true)}>Aprovar</button>
            <button type="button" className="btn-secondary px-2 text-xs" onClick={() => run(api.reviewCharacterArt, ca.id, false)}>Recusar</button>
          </>
        )}
        {isMaster && ca.status === 'ACTIVE' && (
          <button type="button" className="btn-ghost px-2 text-xs" onClick={() => run(api.removeCharacterArt, ca.id)}>Remover arte</button>
        )}
      </div>
      {moves.length > 0 && (
        <ul className="flex flex-col gap-2">
          {moves.map((t) => <MoveRow key={t.id} t={t} isMaster={isMaster} canAct={canAct} run={run} />)}
        </ul>
      )}
      {ca.status === 'ACTIVE' && canAct && (
        <div className="flex flex-wrap gap-2">
          <select className="field m-0 flex-1" aria-label={`Aprender de ${ca.artName}`} value={choice}
            onFocus={loadMoves} onChange={(e) => setChoice(e.target.value)}>
            <option value="">{catalogArt ? 'Escolha uma técnica ou base…' : 'Carregar técnicas e bases…'}</option>
            {options.map((o) => (
              <option key={`${o.kind}:${o.name}`} value={`${o.kind}:${o.name}`}>
                {o.kind === 'BASE' ? 'Base' : 'Técnica'} · {o.name} ({lab(GRADS, o.graduation)})
              </option>
            ))}
          </select>
          <button type="button" className="btn-secondary" disabled={!choice} onClick={() => {
            const [kind, ...rest] = choice.split(':');
            run(api.learnMove, { characterArtId: ca.id, kind, name: rest.join(':') });
            setChoice('');
          }}>{isMaster ? 'Ensinar' : 'Pedir'}</button>
        </div>
      )}
    </section>
  );
}

/** Artes, técnicas e bases da ficha (F3b): jogador pede, Mestre aprova, lança treino e maestria. */
export default function MartialPanel({ sheetId, campaignId, isMaster, isOwner, refreshKey }) {
  const toast = useToast();
  const [data, setData] = useState(null);
  const [catalog, setCatalog] = useState([]);
  const [artChoice, setArtChoice] = useState('');
  const canAct = isOwner || isMaster;

  const load = useCallback(async () => {
    try { setData(await api.getMartial(sheetId)); } catch (err) { toast(parseApiError(err).message, 'error'); }
  }, [sheetId, toast]);
  useEffect(() => { load(); }, [load, refreshKey]);
  useEffect(() => {
    let active = true;
    listArts(campaignId).then((a) => active && setCatalog(a.filter((x) => !x.status || x.status === 'APPROVED'))).catch(() => {});
    return () => { active = false; };
  }, [campaignId]);

  async function run(fn, ...args) {
    try { setData(await fn(sheetId, ...args)); } catch (err) { toast(parseApiError(err).message, 'error'); }
  }

  if (!data) return <div className="skeleton h-16 w-full" />;
  const ownedArts = new Set(data.arts.map((a) => a.artId));
  return (
    <div className="flex flex-col gap-3">
      {data.arts.length === 0 && <p className="text-sm">Nenhuma arte marcial.</p>}
      {data.arts.map((ca) => (
        <ArtCard key={ca.id} ca={ca} moves={data.techniques.filter((t) => t.characterArtId === ca.id)}
          isMaster={isMaster} canAct={canAct} run={run} />
      ))}
      {canAct && (
        <div className="flex flex-wrap gap-2">
          <select className="field m-0 flex-1" aria-label="Arte do catálogo" value={artChoice} onChange={(e) => setArtChoice(e.target.value)}>
            <option value="">Escolha uma arte do catálogo…</option>
            {catalog.filter((a) => !ownedArts.has(a.id)).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
          <button type="button" className="btn-secondary" disabled={!artChoice}
            onClick={() => { run(api.requestArt, artChoice); setArtChoice(''); }}>{isMaster ? 'Adicionar arte' : 'Pedir arte'}</button>
        </div>
      )}
    </div>
  );
}
