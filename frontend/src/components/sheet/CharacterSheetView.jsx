import { useEffect, useState } from 'react';
import SheetTabs from './SheetTabs';
import SheetImagePicker from './SheetImagePicker';
import LimiterRadar from './LimiterRadar';
import InjuryBoard from './InjuryBoard';
import FighterPlaceholder from './FighterPlaceholder';
import SheetVisibilityPanel from './SheetVisibilityPanel';
import { exportSheet, getSheetImageUrl } from '../../api/characterSheets';
import { listTraits } from '../../api/catalog';
import { Effects, Variations } from '../catalog/CatalogManager';
import { approveSheet, updateResources } from '../../api/characterSheets';
import { reopenBuild } from '../../api/characterBuild';
import { parseApiError } from '../../api/parseApiError';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../contexts/ToastContext';
import CharacterSheetForm from './CharacterSheetForm';
import GmAdjustmentPanel from './GmAdjustmentPanel';
import RollsPanel from './RollsPanel';
import ActivityLogPanel from './ActivityLogPanel';
import MartialPanel from './MartialPanel';
import TrainingPanel from './TrainingPanel';
import ProgressPanel from './ProgressPanel';
import NotebookPanel from './NotebookPanel';
import { ATTRS, BODY_TYPES, DOMINANCES, BAND_LABELS, FATIGUE_LABELS, KEIKEN, KEIKEN_MIN, label } from './sheetRules';
import { RARITY, rarityTagStyle } from '../ui/rarity';
import RichText from '../ui/RichText';
import InventoryGrid from './InventoryGrid';
import Icon from '../ui/Icon';
import BleedEdge from '../ui/BleedEdge';
import ConditionFx from '../ui/ConditionFx';
import useSheetConditions from './useSheetConditions';
import { themeFor } from '../combat/conditionThemes';

const kg = (v) => (Math.round((v ?? 0) * 10) / 10).toString().replace('.', ',');

const STATUS = { DRAFT: 'Rascunho', PENDING_APPROVAL: 'Aguardando aprovação', COMPLETED: 'Aprovada', IN_BUILD: 'Em construção' };

const Hidden = ({ name }) => (
  <p className="caption inline-block">Seção oculta{name ? `: ${name}` : ''}</p>
);

// V-11: clique no número edita; Enter ou sair do campo salva, Esc cancela.
function InlineNumber({ value, label: lbl, onSave }) {
  const [draft, setDraft] = useState(null);
  function commit() {
    if (draft !== '' && Number(draft) !== value) onSave(Number(draft));
    setDraft(null);
  }
  if (!onSave) return <strong className="text-base">{value}</strong>;
  if (draft == null) {
    return (
      <button type="button" className="text-base font-bold underline decoration-dotted" title={`Editar ${lbl}`}
        aria-label={`${lbl} atual ${value}, clique para editar`} onClick={() => setDraft(String(value))}>
        {value}
      </button>
    );
  }
  return (
    <input type="number" min={0} autoFocus aria-label={`${lbl} atual`} className="field m-0 inline-block w-20 px-1 py-0 text-base"
      value={draft} onChange={(e) => setDraft(e.target.value)} onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === 'Escape') setDraft(null);
        if (e.key === 'Enter') commit();
      }} />
  );
}

function Meter({ name, value, max, color, onSave }) {
  const pct = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0;
  return (
    <div>
      <div className="flex items-baseline justify-between text-xs uppercase">
        <span className="font-bold">{name}</span>
        <span><InlineNumber value={value} label={name} onSave={onSave} /> / {max}</span>
      </div>
      <div className="meter" role="meter" aria-label={name} aria-valuenow={value} aria-valuemin={0} aria-valuemax={max}>
        <div className="meter-fill" style={{ width: `${pct}%`, '--meter-color': color }} />
      </div>
    </div>
  );
}

const FATIGUE_MAX = 12;
// Barra de desgaste: enche de 0 a 12 (>= 12 = Colapso); cor interpola from->to pelo nível.
function FatigueMeter({ name, points, bandKey, kind, onSave }) {
  const pct = Math.round((Math.min(points, FATIGUE_MAX) / FATIGUE_MAX) * 100);
  const from = `var(--fatigue-${kind}-from)`;
  const to = `var(--fatigue-${kind}-to)`;
  return (
    <div>
      <div className="flex items-baseline justify-between text-xs uppercase">
        <span className="font-bold">Desgaste {name.toLowerCase()}</span>
        <span><InlineNumber value={points} label={`Desgaste ${name.toLowerCase()}`} onSave={onSave} /> / {FATIGUE_MAX} · {FATIGUE_LABELS[bandKey]}</span>
      </div>
      <div className="meter relative" role="meter" aria-label={`Desgaste ${name.toLowerCase()}`}
        aria-valuenow={Math.min(points, FATIGUE_MAX)} aria-valuemin={0} aria-valuemax={FATIGUE_MAX}
        aria-valuetext={`${points} pontos, ${FATIGUE_LABELS[bandKey]}`}>
        <div className="meter-fill" style={{ width: `${pct}%`, '--meter-color': `color-mix(in oklab, ${from} ${100 - pct}%, ${to})` }} />
        {[3, 6, 9].map((m) => (
          <span key={m} aria-hidden="true" className="absolute top-0 h-full"
            style={{ left: `${(m / FATIGUE_MAX) * 100}%`, borderLeft: '2px solid var(--line)' }} />
        ))}
      </div>
      <div className="relative mt-0.5 h-3 text-[10px] opacity-70" aria-hidden="true">
        {[3, 6, 9, 12].map((m) => (
          <span key={m} className="absolute -translate-x-full" style={{ left: `${(m / FATIGUE_MAX) * 100}%` }}>{m}</span>
        ))}
      </div>
    </div>
  );
}

function ProgressChip({ title, value, color, bar }) {
  const pct = bar ? (bar.max > 0 ? Math.min(100, (100 * bar.value) / bar.max) : 100) : 0;
  return (
    <div className="panel flex min-w-20 flex-col items-center px-3 py-1" style={{ borderColor: color }}>
      <span className="panel-title text-3xl leading-none" style={{ color }}>{value}</span>
      <span className="text-[10px] uppercase">{title}</span>
      {bar && (
        <div className="meter mt-1 w-full" style={{ height: 8, borderWidth: 2 }} role="meter" aria-label={`${title} até a próxima patente`}
          aria-valuenow={bar.value} aria-valuemin={0} aria-valuemax={bar.max}>
          <div className="meter-fill" style={{ width: `${bar.value > 0 ? Math.max(pct, 6) : 0}%`, '--meter-color': color }} />
        </div>
      )}
    </div>
  );
}

const svgProps = { width: 20, height: 20, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 2, 'aria-hidden': true };
const ICON = {
  ruler: <svg {...svgProps}><rect x="3" y="8" width="18" height="8" /><path d="M7 8v3M11 8v4M15 8v3M19 8v4" /></svg>,
  scale: <svg {...svgProps}><path d="M12 4v16M6 20h12M5 7h14M5 7l-3 7a3 3 0 006 0zM19 7l-3 7a3 3 0 006 0z" /></svg>,
  hand: <svg {...svgProps}><path d="M8 13V5a1.5 1.5 0 013 0v6M11 11V4a1.5 1.5 0 013 0v7M14 11V6a1.5 1.5 0 013 0v8c0 4-2 6-5 6-3 0-4-2-6-5l-1-2a1.5 1.5 0 012-2l2 2" /></svg>,
  belt: <svg {...svgProps}><path d="M2 10h20v4H2zM12 14l-3 7M12 14l3 7" /><rect x="10" y="9" width="4" height="6" /></svg>,
  pack: <svg {...svgProps}><path d="M6 8a6 6 0 0112 0v12H6zM9 14h6" /></svg>,
};
// Silhueta varia pela largura do tronco: ecto estreito, meso médio, endo largo.
function BodyIcon({ type }) {
  const w = { ECTOMORPH: 3, MESOMORPH: 5, ENDOMORPH: 7 }[type] ?? 5;
  return (
    <svg {...svgProps}>
      <circle cx="12" cy="5" r="2.5" />
      <path d={`M${12 - w} 21c0-5 1-10 ${w}-10s${w} 5 ${w} 10z`} />
    </svg>
  );
}

function IdentityChip({ icon, title, value, color, corner }) {
  return (
    <div className="panel relative flex items-center gap-2 px-3 py-1.5" style={color ? { borderColor: color } : undefined}>
      {corner != null && (
        <span className="tag absolute right-1 top-1 px-1 text-[10px]" title="Modificador de peso" aria-label={`Modificador de peso ${corner}`}>{corner}</span>
      )}
      {icon}
      <div className="leading-tight">
        <div className="text-[10px] uppercase opacity-70">{title}</div>
        <div className="text-sm font-bold">{value}</div>
      </div>
    </div>
  );
}

const fmtMod = (m) => (m == null ? '—' : m > 0 ? `+${m}` : m < 0 ? `−${Math.abs(m)}` : '0');

const INAPT_BANDS = ['COLLAPSE', 'INEPT', 'DEFICIENT', 'UNTRAINED'];

/** Soma o que traços e clãs deram a um atributo: { traits: [[nome, flat]], flat, pct }. */
function gainsFor(sheet, en) {
  const out = { items: [], flat: 0, pct: 0 };
  [...(sheet.traits ?? []), ...(sheet.clans ?? [])].forEach((x) => {
    const g = x.buildGains;
    const f = g?.attributeFlat?.[en] ?? 0;
    const p = g?.attributePercent?.[en] ?? 0;
    if (f || p) { out.items.push(`${x.name}: ${f ? `${f > 0 ? '+' : ''}${f}` : ''}${p ? ` ${p > 0 ? '+' : ''}${p}%` : ''}`.trim()); out.flat += f; out.pct += p; }
  });
  return out;
}

function AttributeCard({ a, v, m, lim, bandKey, gains }) {
  const inapt = INAPT_BANDS.includes(bandKey);
  const pct = lim > 0 ? Math.min(100, Math.round((v / lim) * 100)) : 0;
  return (
    <div className="panel attr-tile">
      <div className="attr-tile-head">
        <span>{a.label}</span>
        <span className="tag" style={inapt && m != null ? { borderColor: 'var(--accent)', color: 'var(--accent)' } : undefined}
          title={m == null ? 'Estado crítico (valor 0)' : 'Modificador'}
          aria-label={m == null ? 'Modificador crítico' : `Modificador ${fmtMod(m)}`}>
          {fmtMod(m)}
        </span>
      </div>
      <div className="flex flex-col gap-1 p-2">
        <div className="panel-title text-4xl leading-none">{v}</div>
        {gains && gains.items.length > 0 && (
          <div className="flex flex-wrap gap-1 text-[10px]" title={gains.items.join('\n')}>
            {gains.flat !== 0 && <span className="tag-accent">{gains.flat > 0 ? '+' : ''}{gains.flat} traços</span>}
            {gains.pct !== 0 && <span className="tag-accent">{gains.pct > 0 ? '+' : ''}{gains.pct}%</span>}
          </div>
        )}
        {lim != null && (
          <div>
            <div className="meter" style={{ height: 8, borderWidth: 2 }} role="meter" aria-label={`${a.label} em relação ao limitador`}
              aria-valuenow={v} aria-valuemin={0} aria-valuemax={lim}>
              <div className="meter-fill" style={{ width: `${pct}%`, '--meter-color': inapt ? 'var(--accent)' : 'var(--text)' }} />
            </div>
            <div className="mt-0.5 text-[10px] uppercase opacity-70">Lim {lim}</div>
          </div>
        )}
        <div className={`text-[10px] uppercase ${inapt ? 'font-bold text-[var(--accent)]' : 'opacity-70'}`}>{BAND_LABELS[bandKey]}</div>
      </div>
    </div>
  );
}

function Block({ id, title, aside, collapsible, defaultOpen = false, children }) {
  const [open, setOpen] = useState(!collapsible || defaultOpen);
  return (
    <section id={id} className="panel scroll-mt-4 p-4">
      <div className={`flex flex-wrap items-start gap-2 ${open ? 'mb-4' : ''}`}>
        {collapsible ? (
          <button type="button" className="flex flex-1 items-start gap-2 text-left" aria-expanded={open}
            onClick={() => setOpen((o) => !o)}>
            <h3 className="panel-title ribbon">{title}</h3>
            <span aria-hidden="true" className="ml-auto text-xs">{open ? '▲' : '▼'}</span>
          </button>
        ) : <h3 className="panel-title ribbon">{title}</h3>}
        {aside && <span className="ml-auto">{aside}</span>}
      </div>
      <div className={`collapsible-body${open ? '' : ' is-collapsed'}`}>{children}</div>
    </section>
  );
}

// V-13: recolhido mostra nome + tags; ao abrir, busca efeitos/variações no catálogo (versão atual, não o snapshot).
function TraitItem({ t, campaignId }) {
  const [full, setFull] = useState();
  async function load(e) {
    if (!e.currentTarget.open || full !== undefined) return;
    try {
      setFull((await listTraits(campaignId)).find((c) => c.id === t.traitId) ?? null);
    } catch {
      setFull(null);
    }
  }
  return (
    <li>
      <details className="bubble p-2 text-sm" onToggle={load}>
        <summary className="flex cursor-pointer flex-wrap items-center gap-2">
          <strong className="panel-title">{t.name}</strong>
          <span className="tag" style={rarityTagStyle(t.rarity)}>{RARITY[t.rarity]?.label ?? t.rarity}</span>
          <span className="tag">{t.acquiredIn === 'CAMPAIGN' ? 'Campanha' : 'Construção'}</span>
        </summary>
        <div className="mt-2 flex flex-col gap-2">
          {t.description && <RichText text={t.description} />}
          {full && <Effects effects={full.effects} />}
          {full && <Variations variations={full.variations} />}
        </div>
      </details>
    </li>
  );
}

export default function CharacterSheetView({ sheet, isMaster: isCampaignMaster, campaignId, refreshKey = 0, onChange, onBack, focusSection }) {
  const { user } = useAuth();
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const condFx = useSheetConditions(campaignId, sheet.id);
  const [reason, setReason] = useState('');
  const isOwner = sheet.ownerUserId === user?.userId;
  const isNpc = sheet.npcId != null;
  // Ficha de NPC: o dono age como Mestre; o Mestre da campanha só lê.
  const isMaster = isNpc ? isOwner : isCampaignMaster;
  const draft = isNpc || sheet.buildStatus === 'DRAFT';

  const FOCUS_TAB = { martial: 'martial', notebook: 'martial', progress: 'progress' };
  const tabKey = `sheetTab:${sheet.id}`;
  const [tab, setTab] = useState(() => {
    if (FOCUS_TAB[focusSection]) return FOCUS_TAB[focusSection];
    try { return sessionStorage.getItem(tabKey) || 'overview'; } catch { return 'overview'; }
  });
  const [printing, setPrinting] = useState(false);
  function selectTab(id) {
    setTab(id);
    try { sessionStorage.setItem(tabKey, id); } catch { /* sem storage */ }
  }
  useEffect(() => {
    const on = () => setPrinting(true);
    const off = () => setPrinting(false);
    window.addEventListener('beforeprint', on);
    window.addEventListener('afterprint', off);
    return () => { window.removeEventListener('beforeprint', on); window.removeEventListener('afterprint', off); };
  }, []);

  // Âncora vinda da aba Aprovações: abre a aba certa e rola até o bloco (de novo quando os painéis assíncronos mudam a altura).
  useEffect(() => {
    if (!focusSection) return undefined;
    if (FOCUS_TAB[focusSection]) setTab(FOCUS_TAB[focusSection]);
    const go = () => document.getElementById(`sheet-${focusSection}`)?.scrollIntoView({ block: 'start' });
    const t0 = setTimeout(go, 50);
    const t = setTimeout(go, 600);
    return () => { clearTimeout(t0); clearTimeout(t); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusSection]);

  // Retrato: imagem autenticada como blob; revoga o object URL ao trocar/desmontar.
  const [portraitUrl, setPortraitUrl] = useState(null);
  useEffect(() => {
    if (!sheet.hasPortrait) return undefined;
    let active = true;
    let url = null;
    getSheetImageUrl(sheet.id, 'PORTRAIT').then((u) => {
      if (active) { url = u; setPortraitUrl(u); } else URL.revokeObjectURL(u);
    }).catch(() => {});
    return () => { active = false; if (url) URL.revokeObjectURL(url); setPortraitUrl(null); };
  }, [sheet.id, sheet.hasPortrait, sheet.version]);

  const [reopenReason, setReopenReason] = useState('');
  async function reopen(e) {
    e.preventDefault();
    try {
      await reopenBuild(sheet.id, { reason: reopenReason.trim() });
      toast('Construção reaberta.');
      setReopenReason('');
      onChange({ ...sheet, buildStatus: 'IN_BUILD' });
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    }
  }

  async function decide(approved) {
    try {
      onChange(await approveSheet(sheet.id, { approved, reason: reason.trim() || null }));
      toast(approved ? 'Ficha aprovada.' : 'Ficha devolvida para ajustes.');
      setReason('');
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    }
  }

  if (editing && isOwner && draft) {
    return (
      <div className="panel p-4">
        <h2 className="panel-title mb-3">Editar ficha</h2>
        <CharacterSheetForm
          campaignId={campaignId}
          npcId={sheet.npcId}
          sheet={sheet}
          onSaved={(s) => { setEditing(false); onChange(s); }}
          onCancel={() => setEditing(false)}
        />
      </div>
    );
  }

  const { resources: r, fatigue: f, progress: p, derived: d } = sheet;
  const canEditResources = isOwner || isMaster;
  const saveResource = (key) => canEditResources ? async (n) => {
    try {
      onChange(await updateResources(sheet.id, { [key]: n }));
      toast('Recursos atualizados.');
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    }
  } : undefined;

  const rev = refreshKey + sheet.version;
  const seesPrivate = isOwner || isMaster;
  const radarAxes = sheet.attributes && r && d?.limiters ? [
    ...ATTRS.filter((a) => a.key !== 'tal').map((a) => ({ key: a.key, label: a.label, value: sheet.attributes[a.enumName], limit: d.limiters[a.enumName] })),
    { key: 'hp', label: 'HP', value: r.hpMax, limit: d.hpLimiter },
    { key: 'sp', label: 'SP', value: r.spMax, limit: d.spLimiter },
    { key: 'kp', label: 'PQ', value: r.kpMax, limit: d.kpLimiter },
  ] : null;

  const attrBlock = (
      <Block title="Atributos">
        {!sheet.attributes ? <Hidden name="atributos" /> : (
          <div className="sheet-attrs">
            {ATTRS.map((a) => (
              <AttributeCard key={a.key} a={a} v={sheet.attributes[a.enumName]} m={d?.mods?.[a.enumName]} lim={d?.limiters?.[a.enumName]}
                bandKey={d?.bands?.[a.enumName]} gains={gainsFor(sheet, a.enumName)} />
            ))}
          </div>
        )}
      </Block>
  );
  const traitsBlock = (
      <Block title="Traços e clãs">
        {!sheet.traits || !sheet.clans ? <Hidden name="traços e clãs" /> : (
          <div className="flex flex-col gap-3">
            {sheet.traits.length === 0 ? <p className="text-sm">Nenhum traço.</p> : (
              <ul className="sheet-traits">
                {sheet.traits.map((t) => <TraitItem key={t.id} t={t} campaignId={campaignId} />)}
              </ul>
            )}
            {sheet.clans.map((c) => (
              <div key={c.id} className="bubble p-2 text-sm">
                <strong>Clã {c.name}</strong>
                {c.heritage?.length > 0 && (
                  <ul className="mt-1 list-disc pl-5">
                    {c.heritage.map((h) => <li key={h}><RichText text={h} /></li>)}
                  </ul>
                )}
              </div>
            ))}
          </div>
        )}
      </Block>
  );

  const overview = (
    <div className="sheet-overview">
      <div className="sheet-col">
        <div className="portrait-frame">
          {portraitUrl
            ? <img src={portraitUrl} alt={`Retrato de ${sheet.name}`} />
            : <FighterPlaceholder className="portrait-ph" />}
        </div>
        <div className="panel hero-card p-3">
          <div className="grid grid-cols-2 gap-2" aria-label="Identidade">
            <IdentityChip icon={ICON.ruler} title="Altura" value={`${sheet.heightM} m`} />
            <IdentityChip icon={<BodyIcon type={sheet.bodyType} />} title="Tipo corporal" value={label(BODY_TYPES, sheet.bodyType)} />
            <IdentityChip icon={ICON.hand} title="Dominância" value={label(DOMINANCES, sheet.dominance)} />
            <IdentityChip icon={ICON.scale} title="Peso" value={`${sheet.weightKg} kg`} corner={d?.weightMod} />
            {d && <IdentityChip icon={ICON.pack} title="Carga" value={`${kg(d.carriedKg)} / ${d.carryKg} kg`} />}
            {p && <IdentityChip icon={ICON.belt} title="Keiken" value={KEIKEN[p.keiken]} color="var(--keiken)" />}
          </div>
        </div>
        {seesPrivate && (
          <Block title="Imagens do lutador" collapsible>
            <SheetImagePicker sheet={sheet} onChange={onChange} />
          </Block>
        )}
      </div>

      <div className="sheet-col">
        {condFx.length > 0 && (
          <div className="panel flex flex-wrap items-center gap-2 p-2" aria-label="Condições em combate">
            <span className="caption">Em combate</span>
            {condFx.map((c) => {
              const t = themeFor(c.key, c.name);
              return (
                <span key={c.id} className="cond-chip" style={{ '--fx-color': t.color }}>
                  <Icon name={t.icon} size={16} />{t.word} {c.name}
                </span>
              );
            })}
          </div>
        )}
        {!isNpc && isMaster && sheet.buildStatus === 'PENDING_APPROVAL' && (
          <Block title="Aprovação">
            <label className="label">
              Motivo (opcional)
              <input className="field mt-1 w-full" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
            </label>
            <div className="mt-3 flex gap-2">
              <button type="button" className="btn-primary" onClick={() => decide(true)}>Aprovar</button>
              <button type="button" className="btn-secondary" onClick={() => decide(false)}>Devolver</button>
            </div>
          </Block>
        )}
        <Block title="Recursos">
          {!r ? <Hidden name="recursos" /> : (
            <>
              <div className="flex flex-col gap-3">
                <Meter name="HP" value={r.currentHp} max={r.effectiveHpMax} color="var(--meter-hp)" onSave={saveResource('currentHp')} />
                <Meter name="SP" value={r.currentSp} max={r.spMax} color="var(--meter-sp)" onSave={saveResource('currentSp')} />
                <Meter name="PQ" value={r.currentKp} max={r.kpMax} color="var(--meter-kp)" onSave={saveResource('currentKp')} />
              </div>
              <p className="mt-3 text-sm">
                Feridas abertas: <strong>{r.openWounds}</strong>
                {r.effectiveHpMax !== r.hpMax && ` · HP máx. base ${r.hpMax}`}
              </p>
              {f && (
                <div className="mt-3 flex flex-col gap-2">
                  <p className="caption">Desgaste (cansaço)</p>
                  <FatigueMeter name="Físico" points={f.physical} bandKey={f.physicalBand} kind="physical" onSave={saveResource('physicalFatigue')} />
                  <FatigueMeter name="Mental" points={f.mental} bandKey={f.mentalBand} kind="mental" onSave={saveResource('mentalFatigue')} />
                </div>
              )}
              {p && (
                <div className="mt-3 flex flex-wrap gap-2">
                  <ProgressChip title="EL" value={p.fightExperience} color="var(--chip-el)"
                    bar={{ value: p.fightExperience - (KEIKEN_MIN[p.keiken] ?? 0), max: p.nextKeikenAt == null ? 0 : p.nextKeikenAt - (KEIKEN_MIN[p.keiken] ?? 0) }} />
                  <ProgressChip title="PS pendentes" value={p.pendingOvercomePoints} color="var(--chip-ps)" />
                </div>
              )}
            </>
          )}
        </Block>
        <Block title="Limitadores">
          {!radarAxes ? <Hidden name="atributos ou recursos" /> : (
            <div className="flex flex-col items-center gap-2">
              <LimiterRadar axes={radarAxes} />
              <p className="hint">O anel externo é o limitador. Vértice destacado = acima do limitador.</p>
            </div>
          )}
        </Block>
      </div>

      <div className="sheet-col">
        <Block title="Ferimentos">
          {!sheet.injuries ? <Hidden name="ferimentos" /> : (
            <InjuryBoard sheet={sheet} isMaster={isMaster} onChange={onChange} />
          )}
        </Block>
        {attrBlock}
      </div>
    </div>
  );

  const martial = seesPrivate && (
    <div className="flex flex-col gap-4">
      <Block id="sheet-martial" title="Artes marciais">
        <MartialPanel sheetId={sheet.id} campaignId={campaignId} isMaster={isMaster} isOwner={isOwner} refreshKey={rev} />
      </Block>
      {!isNpc && (
        <>
          <Block id="sheet-notebook" title="Caderno Marcial e Forja">
            <NotebookPanel sheetId={sheet.id} isMaster={isMaster} isOwner={isOwner} refreshKey={rev} />
          </Block>
          <Block title="Treinos" collapsible defaultOpen>
            <TrainingPanel sheetId={sheet.id} refreshKey={rev} />
          </Block>
        </>
      )}
    </div>
  );

  const inventory = (
    <Block title="Inventário">
      {sheet.money == null || !sheet.inventory ? <Hidden name="inventário" /> : (
        <InventoryGrid items={sheet.inventory} money={sheet.money} derived={d} />
      )}
    </Block>
  );

  const hasProgress = seesPrivate && !isNpc && p;
  const progress = hasProgress && (
    <Block id="sheet-progress" title="Progresso">
      <ProgressPanel sheetId={sheet.id} progress={p} campaignId={campaignId} isMaster={isMaster} isOwner={isOwner} refreshKey={rev} />
    </Block>
  );

  const rolls = seesPrivate && (
    <Block title="Rolagens">
      <RollsPanel sheetId={sheet.id} campaignId={campaignId} isMaster={isMaster} isNpc={isNpc} refreshKey={rev} />
    </Block>
  );

  const canEditDraft = isOwner && draft;
  const showVisibility = !isNpc && seesPrivate && sheet.campaignId && sheet.ownerUserId;
  const showReopen = !isNpc && isMaster && ['COMPLETED', 'PENDING_APPROVAL'].includes(sheet.buildStatus);
  const hasGm = canEditDraft || seesPrivate || isMaster;
  const gm = hasGm && (
    <div className="flex flex-col gap-4">
      {canEditDraft && (
        <button type="button" className="btn-secondary self-start" onClick={() => setEditing(true)}>Editar ficha</button>
      )}
      {isMaster && (
        <Block title="Ajustes do Mestre">
          <GmAdjustmentPanel sheet={sheet} campaignId={campaignId} onDone={onChange} />
        </Block>
      )}
      {showReopen && (
        <Block title="Reabrir construção">
          <form onSubmit={reopen} className="flex flex-col gap-2 sm:flex-row sm:items-end">
            <label className="flex-1 text-xs uppercase">
              Motivo (obrigatório)
              <input required maxLength={500} className="field mt-1 w-full" value={reopenReason} onChange={(e) => setReopenReason(e.target.value)} />
            </label>
            <button type="submit" className="btn-secondary" disabled={!reopenReason.trim()}>Reabrir construção</button>
          </form>
        </Block>
      )}
      {showVisibility && (
        <Block title="Visível para os outros jogadores" collapsible>
          <SheetVisibilityPanel campaignId={sheet.campaignId} ownerUserId={sheet.ownerUserId} isMaster={isMaster} />
        </Block>
      )}
      {seesPrivate && (
        <Block title="Atividade da ficha" collapsible>
          <ActivityLogPanel sheetId={sheet.id} refreshKey={rev} />
        </Block>
      )}
    </div>
  );

  const tabs = [
    { id: 'overview', label: 'Visão Geral', node: <div className="flex flex-col gap-4">{overview}{traitsBlock}</div> },
    seesPrivate && { id: 'martial', label: 'Artes Marciais', node: martial },
    { id: 'inventory', label: 'Inventário', node: inventory },
    hasProgress && { id: 'progress', label: 'Progressão', node: progress },
    seesPrivate && { id: 'rolls', label: 'Rolagens', node: rolls },
    hasGm && { id: 'gm', label: 'Mestre', node: gm },
  ].filter(Boolean);
  const current = tabs.find((t) => t.id === tab) ?? tabs[0];

  return (
    <div className="page-gutter flex flex-col">
      {r && <BleedEdge wounds={r.openWounds} page />}
      <ConditionFx entries={[...(r?.openWounds > 0 ? [{ id: `w${r.openWounds}`, anchorId: null, key: 'FERIDA', name: `Feridas abertas ${r.openWounds}` }] : []), ...condFx]} />
      <div className="flex flex-wrap items-center gap-3">
        {onBack && <button type="button" className="btn-ghost" onClick={onBack}><Icon name="back" size={18} /> Fichas</button>}
        <h2 className="panel-title flex-1 text-2xl">{sheet.name}</h2>
        {!isNpc && <span className="tag-accent tag">{STATUS[sheet.buildStatus]}</span>}
        {d?.criticalState && <span className="tag">Estado crítico</span>}
        <button type="button" className="btn-secondary no-print" onClick={async () => {
          try { await exportSheet(sheet.id, sheet.name); } catch (err) { toast(parseApiError(err).message, 'error'); }
        }}>Exportar JSON</button>
        <button type="button" className="btn-secondary no-print" onClick={() => {
          // abre os traços antes para dar tempo de carregar efeitos/variações
          window.dispatchEvent(new Event('beforeprint'));
          setTimeout(() => window.print(), 600);
        }}>Imprimir / PDF</button>
      </div>
      {!isNpc && <p className="caption self-start">Jogador · {sheet.ownerName}</p>}
      {printing ? (
        <div className="flex flex-col gap-6">
          {tabs.map((t) => (
            <section key={t.id} className="flex flex-col gap-3">
              <h2 className="panel-title text-xl">{t.label}</h2>
              {t.node}
            </section>
          ))}
        </div>
      ) : (
        <>
          <SheetTabs tabs={tabs} active={current.id} onChange={selectTab} idBase={`sheet-${sheet.id}`} />
          <div role="tabpanel" id={`sheet-${sheet.id}-panel`} aria-labelledby={`sheet-${sheet.id}-tab-${current.id}`}
            tabIndex={0} className="sheet-tabpanel">
            {current.node}
          </div>
        </>
      )}
    </div>
  );
}
