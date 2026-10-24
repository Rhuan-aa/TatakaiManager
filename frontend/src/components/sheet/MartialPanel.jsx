import { useCallback, useEffect, useState } from 'react';
import { getArt, listArts } from '../../api/catalog';
import * as api from '../../api/characterTechniques';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { ART_CATEGORIES, GRADS, lab } from '../catalog/artLabels';
import { KEIKEN } from './sheetRules';
import RichText from '../ui/RichText';

const STATES = { REQUESTED: 'Pedida', IN_TRAINING: 'Em treino', ACTIVE: 'Ativa', STORED: 'Guardada' };
const MASTERY = [
  { value: 'SKETCH', label: 'Esboço' }, { value: 'UNCORRECTED', label: 'Não Corrigida' },
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

function NumberAction({ label, onSubmit, min, max }) {
  const [v, setV] = useState('');
  return (
    <span className="flex items-center gap-1">
      <input type="number" min={min} max={max} className="field m-0 h-7 w-12 px-1 py-0 text-xs" aria-label={label} value={v}
        onChange={(e) => setV(e.target.value)} />
      <button type="button" className="btn-ghost px-1 text-xs" disabled={v === '' || Number(v) < min || (max != null && Number(v) > max)}
        onClick={() => { onSubmit(Number(v)); setV(''); }}>{label}</button>
    </span>
  );
}

const MASTERY_COLOR = {
  SKETCH: 'var(--mastery-sketch)', UNCORRECTED: 'var(--mastery-uncorrected)', STANDARD: 'var(--mastery-standard)',
  IMPROVED: 'var(--mastery-improved)', SPECIALIZED: 'var(--mastery-specialized)', MASTERED: 'var(--mastery-mastered)',
};
const colorTag = (color) => ({ borderColor: color, color });

/** Técnica/base: recolhida mostra tipo, graduação, estado e maestria; aberta, números, ações e bonificações. */
function MoveRow({ t, isMaster, canAct, run }) {
  const [open, setOpen] = useState(false);
  const [bonus, setBonus] = useState('');
  const learned = t.state === 'ACTIVE' || t.state === 'STORED';
  const canLevel = learned && t.nextLevelCost != null && t.masteryPoints >= t.nextLevelCost;
  return (
    <li className="card-round flex flex-col gap-2 text-sm">
      <button type="button" className="flex flex-wrap items-center gap-2 text-left" aria-expanded={open}
        onClick={() => setOpen((o) => !o)}>
        <span className="flex flex-1 flex-col gap-1">
          <strong className="panel-title text-base">{t.name}</strong>
          <span className="flex flex-wrap gap-1">
            <span className="tag" style={colorTag('var(--stat-complexity)')}>Complexidade {t.complexity}</span>
            <span className="tag" style={colorTag('var(--stat-training)')}>Pontos de treino {t.trainingPoints}</span>
            {t.borrowedFrom && <span className="tag">Emprestada · {t.borrowedFrom}</span>}
          </span>
        </span>
        <span className="tag">{t.kind === 'BASE' ? 'Base' : 'Técnica'}</span>
        <span className="tag">{lab(GRADS, t.graduation)}</span>
        <span className={t.state === 'ACTIVE' ? 'tag-accent tag' : 'tag'}>{STATES[t.state]}</span>
        {learned && <span className="tag" style={colorTag(MASTERY_COLOR[t.mastery])}>{lab(MASTERY, t.mastery)}</span>}
        <span aria-hidden="true" className="text-xs">{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        <>
          {(t.state === 'IN_TRAINING' || (learned && !isMaster && t.nextLevelCost != null)) && (
            <div className="flex flex-wrap items-baseline gap-4">
              {t.state === 'IN_TRAINING' && (
                <span className="flex items-baseline gap-1">
                  <span className="text-fine">Treino</span>
                  <strong className="panel-title text-base leading-none">{t.trainingProgress}</strong>
                  <span className="panel-title text-sm opacity-70">/ {t.trainingPoints}</span>
                </span>
              )}
              {learned && !isMaster && t.nextLevelCost != null && (
                <span className="flex items-baseline gap-1">
                  <span className="text-fine">Maestria</span>
                  <strong className="panel-title text-base leading-none">{t.masteryPoints}</strong>
                  <span className="panel-title text-sm opacity-70">/ {t.nextLevelCost}</span>
                  <span className="text-fine ml-1">para o próximo nível</span>
                </span>
              )}
            </div>
          )}
          <div className="flex flex-wrap items-center gap-2">
            {isMaster && t.state === 'REQUESTED' && (
              <>
                <button type="button" className="btn-primary px-2 text-xs" onClick={() => run(api.reviewTechnique, t.id, true)}>Aprovar</button>
                <button type="button" className="btn-secondary px-2 text-xs" onClick={() => run(api.reviewTechnique, t.id, false)}>Recusar</button>
              </>
            )}
            {isMaster && t.state === 'IN_TRAINING' && <NumberAction label="+ treino" min={1} onSubmit={(n) => run(api.trainTechnique, t.id, n)} />}
            {isMaster && learned && t.nextLevelCost != null && (
              <span className="flex items-center gap-2">
                {t.masteryPoints < t.nextLevelCost && (
                  <NumberAction label="+ maestria" min={1} max={t.nextLevelCost - t.masteryPoints}
                    onSubmit={(n) => run(api.addMasteryPoints, t.id, n)} />
                )}
                <span className="flex items-baseline gap-1">
                  <strong className="panel-title text-base leading-none">{t.masteryPoints}</strong>
                  <span className="panel-title text-sm opacity-70">/ {t.nextLevelCost}</span>
                  <span className="text-fine ml-1">para o próximo nível</span>
                </span>
              </span>
            )}
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
              <select className="field m-0 w-auto py-0.5 text-xs" aria-label={`Degrau de maestria de ${t.name}`} value={t.mastery}
                onChange={(e) => run(api.setMastery, t.id, e.target.value)}>
                {MASTERY.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
              </select>
            )}
            {isMaster && <button type="button" className="btn-ghost px-2 text-xs" onClick={() => run(api.removeTechnique, t.id)}>Remover</button>}
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-fine opacity-70">Bonificações</span>
            {t.bonuses.length === 0 ? <span className="text-fine">Nenhuma ainda.</span> : (
              <div className="flex flex-wrap gap-1">
                {t.bonuses.map((b, i) => <span key={i} className="tag" style={colorTag('var(--chip-el)')}>{BONUS_LABEL[b] ?? b}</span>)}
              </div>
            )}
          </div>
        </>
      )}
    </li>
  );
}

const REQUIREMENTS = {
  COMPLETENESS: 'Completude', SPECIALIZATION: 'Especialização', KEIKEN: 'Keiken', POINTS: 'Pontos de graduação',
  DUEL: 'Duelo',
};

/** RN-PROG-09: requisitos da próxima graduação (tags) e, embaixo, pontos ao lado do botão de graduar. */
function GraduationInfo({ ca, isMaster, run }) {
  const [duel, setDuel] = useState(false);
  const next = GRAD_ORDER[GRAD_ORDER.indexOf(ca.graduation) + 1];
  const needsDuel = ca.requirements.some((r) => r.key === 'DUEL');
  const ready = ca.requirements.every((r) => r.met || (r.key === 'DUEL' && duel));
  return (
    <div className="flex flex-col gap-2">
      <ul className="flex flex-wrap gap-1" aria-label={`Requisitos para ${lab(GRADS, next)}`}>
        {ca.requirements.map((r) => (
          <li key={r.key} className={r.met ? 'tag-accent tag' : 'tag'} title={r.detail}>
            {r.met ? '✓' : '○'} {REQUIREMENTS[r.key] ?? r.key}{r.key === 'KEIKEN' && `: ${KEIKEN[r.detail] ?? r.detail}`}
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap items-center gap-3">
        <span className="flex items-baseline gap-1">
          <span className="panel-title text-2xl leading-none">{ca.graduationPoints}</span>
          <span className="panel-title text-base opacity-70">/ {ca.graduationCost}</span>
          <span className="text-fine ml-1">pontos rumo a {lab(GRADS, next)}</span>
        </span>
        {isMaster && needsDuel && (
          <label className="flex items-center gap-1 text-xs">
            <input type="checkbox" checked={duel} onChange={(e) => setDuel(e.target.checked)} /> Venceu o duelo
          </label>
        )}
        {isMaster && (
          <button type="button" className="btn-primary px-2 text-xs" disabled={!ready}
            onClick={() => run(api.graduateArt, ca.id, duel)}>Graduar</button>
        )}
        {isMaster && <NumberAction label="± pontos" min={-99} onSubmit={(n) => run(api.addGraduationPoints, ca.id, n)} />}
      </div>
    </div>
  );
}

/** Passiva da arte: recolhida mostra só o nome. */
function PassiveCard({ pa }) {
  const [open, setOpen] = useState(false);
  return (
    <li className="card-round flex flex-col gap-1">
      <button type="button" className="flex items-center gap-2 text-left" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <strong className="panel-title flex-1 text-base">{pa.name}</strong>
        {pa.group && pa.group.toLowerCase() !== 'passivas' && <span className="tag">{pa.group}</span>}
        <span aria-hidden="true" className="text-xs">{open ? '▲' : '▼'}</span>
      </button>
      {open && pa.description && <RichText text={pa.description} />}
    </li>
  );
}

function MoveList({ title, moves, ...rest }) {
  if (moves.length === 0) return null;
  return (
    <div className="flex flex-col gap-2">
      <h5 className="text-xs font-bold uppercase tracking-wider opacity-80">{title}</h5>
      <ul className="flex flex-col gap-2">
        {moves.map((t) => <MoveRow key={t.id} t={t} {...rest} />)}
      </ul>
    </div>
  );
}

/** Uma arte na ficha: sem contorno próprio (só espaçamento), com bases, técnicas e passivas separadas. */
function ArtCard({ ca, moves, isMaster, canAct, run }) {
  const toast = useToast();
  const [catalogArt, setCatalogArt] = useState(null);
  const [choice, setChoice] = useState('');
  const owned = new Set(moves.map((m) => `${m.kind}:${m.name}`));
  const options = catalogArt ? [
    ...(catalogArt.bases ?? []).map((b) => ({ kind: 'BASE', name: b.name, graduation: b.graduation ?? 'GENERIC' })),
    ...(catalogArt.techniques ?? []).map((t) => ({ kind: 'TECHNIQUE', name: t.name, graduation: t.graduation ?? 'GENERIC' })),
    ...(catalogArt.borrowed ?? []).map((b) => ({ kind: 'TECHNIQUE', name: b.name, graduation: b.graduation, from: b.art })),
  ].filter((o) => !owned.has(`${o.kind}:${o.name}`) && GRAD_ORDER.indexOf(o.graduation) <= GRAD_ORDER.indexOf(ca.graduation)) : [];

  useEffect(() => {
    let active = true;
    getArt(ca.artId).then((a) => active && setCatalogArt(a)).catch((err) => active && toast(parseApiError(err).message, 'error'));
    return () => { active = false; };
  }, [ca.artId, toast]);

  const sub = { isMaster, canAct, run };
  const passives = catalogArt?.passives ?? [];
  return (
    <section className="flex flex-col gap-3 py-2">
      <div className="flex flex-wrap items-center gap-2">
        <h4 className="panel-title flex-1 text-2xl">{ca.artName}</h4>
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
      {ca.graduationCost != null && <GraduationInfo ca={ca} isMaster={isMaster} run={run} />}
      <MoveList title="Bases" moves={moves.filter((m) => m.kind === 'BASE')} {...sub} />
      <MoveList title="Técnicas" moves={moves.filter((m) => m.kind === 'TECHNIQUE')} {...sub} />
      {passives.length > 0 && (
        <div className="flex flex-col gap-2">
          <h5 className="text-xs font-bold uppercase tracking-wider opacity-80">Passivas</h5>
          <ul className="flex flex-col gap-2 text-sm">
            {passives.map((pa) => (
              <PassiveCard key={`${pa.group}:${pa.name}`} pa={pa} />
            ))}
          </ul>
        </div>
      )}
      {ca.status === 'ACTIVE' && canAct && (
        <div className="flex flex-wrap gap-2">
          <select className="field m-0 flex-1" aria-label={`Aprender de ${ca.artName}`} value={choice}
            onChange={(e) => setChoice(e.target.value)}>
            <option value="">{catalogArt ? 'Escolha uma técnica ou base…' : 'Carregando técnicas e bases…'}</option>
            {options.map((o) => (
              <option key={`${o.kind}:${o.name}`} value={`${o.kind}:${o.name}`}>
                {o.kind === 'BASE' ? 'Base' : o.from ? `Emprestada (${o.from})` : 'Técnica'} · {o.name} ({lab(GRADS, o.graduation)})
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
  const loose = data.techniques.filter((t) => !t.characterArtId);
  return (
    <div className="flex flex-col gap-6">
      {data.arts.length === 0 && <p className="text-sm">Nenhuma arte marcial.</p>}
      {data.arts.map((ca) => (
        <ArtCard key={ca.id} ca={ca} moves={data.techniques.filter((t) => t.characterArtId === ca.id)}
          isMaster={isMaster} canAct={canAct} run={run} />
      ))}
      {loose.length > 0 && (
        <section className="flex flex-col gap-2 py-2">
          <h4 className="panel-title text-2xl">Avulsas</h4>
          <p className="text-fine">Vindas da Forja; não ocupam espaços de arte.</p>
          <MoveList title="Bases" moves={loose.filter((m) => m.kind === 'BASE')} isMaster={isMaster} canAct={canAct} run={run} />
          <MoveList title="Técnicas" moves={loose.filter((m) => m.kind === 'TECHNIQUE')} isMaster={isMaster} canAct={canAct} run={run} />
        </section>
      )}
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
