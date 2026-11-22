import { useEffect, useState } from 'react';
import { getSheetActivityLog, getCampaignActivityLog } from '../../api/characterSheets';
import { parseApiError } from '../../api/parseApiError';
import Icon from '../ui/Icon';

const CATEGORIES = {
  BUILD: 'Construção',
  ROLL: 'Rolagem',
  RESOURCE: 'Recursos',
  CONDITION: 'Condições',
  INVENTORY: 'Inventário',
  GM_ADJUSTMENT: 'Ajuste do Mestre',
  PROGRESSION: 'Progressão',
};
const ICONS = { BUILD: 'build', ROLL: 'dice', RESOURCE: 'heart', CONDITION: 'bandage', INVENTORY: 'pack', GM_ADJUSTMENT: 'scale', PROGRESSION: 'star' };
const FIELDS = {
  HP: 'PV', SP: 'PE', KP: 'PQ', HP_MAX: 'PV máx.', SP_MAX: 'PE máx.', KP_MAX: 'PQ máx.',
  fightExperience: 'EL', pendingOvercomePoints: 'PS pendentes', openWounds: 'feridas abertas',
  physicalFatigue: 'desgaste físico', mentalFatigue: 'desgaste mental', money: 'dinheiro', item: 'item', injury: 'ferimento',
};
const BUILD_ACTIONS = {
  CREATE_SHEET: 'criou a ficha',
  UPDATE_SHEET: 'editou a ficha',
  SUBMIT_SHEET: 'enviou a ficha para aprovação',
  APPROVE_SHEET: 'aprovou a ficha',
  REJECT_SHEET: 'devolveu a ficha para ajustes',
  START_BUILD: 'iniciou a construção da ficha',
  SET_IDENTITY: 'definiu a identidade da ficha',
  ROLL_TRAITS: 'rolou a quantidade de traços',
  CHOOSE_TRAITS: 'escolheu os traços',
  RESOLVE_TRAIT_SLOT: 'resolveu um espaço de traço',
  ROLL_BLOOD_TEST: 'fez o teste de sangue',
  CHOOSE_CLANS: 'escolheu os clãs',
  ROLL_CLANS: 'rolou o clã',
  ROLL_ATTRIBUTES: 'rolou os atributos',
  ASSIGN_ATTRIBUTES: 'distribuiu os atributos',
  SET_FREE_POINTS: 'distribuiu os pontos livres',
  CONFIRM_STEP: 'confirmou um passo da construção',
  GO_BACK: 'voltou um passo da construção',
  SUBMIT_BUILD: 'concluiu a construção',
  REOPEN_BUILD: 'reabriu a construção da ficha',
};
const ACTION_VERBS = {
  GRANT_FIGHT_EXPERIENCE: 'concedeu EL',
  ADJUST_OVERCOME_POINTS: 'ajustou PS',
  SET_OPEN_WOUNDS: 'ajustou as feridas abertas',
  ADD_INJURY: 'registrou um ferimento',
  TREAT_INJURY: 'tratou um ferimento',
  REMOVE_INJURY: 'removeu um ferimento',
  ADJUST_FATIGUE: 'ajustou o desgaste',
  ADJUST_MONEY: 'ajustou o dinheiro',
  ADD_ITEM: 'adicionou um item',
  UPDATE_ITEM: 'atualizou um item',
  REMOVE_ITEM: 'removeu um item',
  ADD_TRAIT: 'adicionou um traço',
  REMOVE_TRAIT: 'removeu um traço',
  REQUEST_ART: 'pediu uma arte marcial',
  ADD_ART: 'adicionou uma arte marcial',
  APPROVE_ART: 'aprovou uma arte marcial',
  REJECT_ART: 'recusou uma arte marcial',
  SET_ART_GRADUATION: 'mudou a graduação',
  REMOVE_ART: 'removeu uma arte marcial',
  REQUEST_TECHNIQUE: 'pediu uma técnica',
  ADD_TECHNIQUE: 'ensinou uma técnica',
  APPROVE_TECHNIQUE: 'aprovou uma técnica',
  REJECT_TECHNIQUE: 'recusou uma técnica',
  TRAIN_TECHNIQUE: 'lançou treino',
  SET_TECHNIQUE_STATE: 'alternou ativa/guardada',
  ADJUST_MASTERY_POINTS: 'ajustou pontos de maestria',
  LEVEL_UP: 'subiu a maestria',
  SET_MASTERY: 'definiu o degrau de maestria',
  REMOVE_TECHNIQUE: 'removeu uma técnica',
  USE_CONSUMABLE: 'usou um consumível',
};
// cor do trilho/nó por categoria (tokens do tema, funcionam nos dois temas)
const COLORS = {
  BUILD: 'var(--rarity-rare)', ROLL: 'var(--highlight)', RESOURCE: 'var(--blood)', CONDITION: 'var(--rarity-epic)',
  INVENTORY: 'var(--stat-function)', GM_ADJUSTMENT: 'var(--accent)', PROGRESSION: 'var(--keiken)',
};
const ROLES = { PLAYER: 'Jogador', MASTER: 'Mestre', SYSTEM: 'Sistema' };

const dayKey = (iso) => new Date(iso).toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' });
const hour = (iso) => new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
const val = (v) => (v == null ? '—' : v);

/** Frase em pt-BR para uma entrada do log (sem o nome do ator). */
function sentence(e) {
  const fieldKey = (e.field ?? '').split(':')[0];
  const field = FIELDS[fieldKey] ?? (e.field?.includes(':') ? e.field.split(':').slice(1).join(':') : e.field);
  const change = e.beforeValue != null || e.afterValue != null;
  let text;
  if (e.action === 'ROLL') text = `rolou ${val(e.afterValue)}`;
  else if (BUILD_ACTIONS[e.action]) text = BUILD_ACTIONS[e.action];
  else if (e.action === 'UPDATE_RESOURCE' || e.action === 'SET_RESOURCE' || e.action === 'SET_MAX' || e.action === 'SET_ATTRIBUTE') {
    const verb = e.action === 'UPDATE_RESOURCE' ? 'alterou' : 'ajustou';
    text = `${verb} ${field ?? 'um valor'}${change ? ` de ${val(e.beforeValue)} para ${val(e.afterValue)}` : ''}`;
  } else {
    text = ACTION_VERBS[e.action] ?? e.action.toLowerCase().replaceAll('_', ' ');
    if (change) text += ` (${field ? `${field}: ` : ''}${val(e.beforeValue)} → ${val(e.afterValue)})`;
  }
  return text + (e.reason ? ` — ${e.action === 'ROLL' ? 'para' : 'motivo:'} ${e.reason}` : '');
}

function Timeline({ entries }) {
  if (!entries.length) return <p className="caption inline-block">Nenhuma atividade registrada.</p>;
  const days = [];
  entries.forEach((e) => {
    const k = dayKey(e.createdAt);
    const last = days[days.length - 1];
    if (last?.k === k) last.items.push(e);
    else days.push({ k, items: [e] });
  });
  return (
    <div className="flex flex-col gap-2">
      {days.map((d) => (
        <section key={d.k} aria-label={d.k}>
          <h4 className="feed-day"><span className="capitalize">{d.k}</span></h4>
          <ol className="feed">
            {d.items.map((e) => {
              const color = COLORS[e.category] ?? 'var(--line)';
              return (
                <li key={e.id} className="feed-item">
                  <span className="feed-node" style={{ '--node-bg': color, '--node-fg': 'var(--ink)' }}>
                    <Icon name={ICONS[e.category] ?? 'circle'} size={18} />
                  </span>
                  <div className="feed-card" style={{ '--rail': color }}>
                    <header className="feed-head">
                      <span className="feed-author">{e.actorRole === 'MASTER' ? 'Mestre' : (e.actorName ?? 'Sistema')}</span>
                      <span className="feed-kind">{CATEGORIES[e.category] ?? e.category}</span>
                      <time className="feed-time" dateTime={e.createdAt}><Icon name="clock" size={12} />{hour(e.createdAt)}</time>
                    </header>
                    <p className="feed-body text-sm">{sentence(e)}</p>
                    {(e.actorRole !== 'MASTER' || e.actorName) && (
                      <p className="text-fine px-[14px] pb-3 -mt-2">
                        {e.actorRole === 'MASTER' && e.actorName ? `${e.actorName} · ` : ''}{ROLES[e.actorRole] ?? e.actorRole}
                      </p>
                    )}
                  </div>
                </li>
              );
            })}
          </ol>
        </section>
      ))}
    </div>
  );
}

/**
 * Props: { entries } (lista pronta) | { sheetId, refreshKey } (busca da ficha)
 * | { campaignId, sheets, refreshKey } (visão do Mestre, com filtros).
 */
export default function ActivityLogPanel({ entries, sheetId, campaignId, sheets = [], refreshKey = 0 }) {
  const [data, setData] = useState(entries ?? []);
  const [category, setCategory] = useState('');
  const [sheetFilter, setSheetFilter] = useState('');
  const [actor, setActor] = useState('');
  const [text, setText] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(!entries);

  useEffect(() => {
    if (entries) return undefined;
    let active = true;
    const req = campaignId
      ? getCampaignActivityLog(campaignId, { sheetId: sheetFilter, category })
      : getSheetActivityLog(sheetId);
    req
      .then((d) => active && (setData(d), setError('')))
      .catch((e) => active && setError(parseApiError(e).message))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [entries, sheetId, campaignId, sheetFilter, category, refreshKey]);

  const q = text.trim().toLowerCase();
  const list = (entries ?? data).filter(
    (e) =>
      (!actor || (e.actorName ?? '').toLowerCase().includes(actor.toLowerCase())) &&
      // No modo campanha a categoria já filtra no servidor; refiltrar é inofensivo.
      (!category || e.category === category) &&
      (!q || `${e.actorName ?? ''} ${sentence(e)}`.toLowerCase().includes(q)),
  );

  return (
    <div className="flex flex-col gap-3">
      <div className="feed-toolbar">
        <label className="feed-search">
          <span className="feed-search-icon"><Icon name="search" size={16} /></span>
          <input type="search" aria-label="Buscar na atividade" className="field" value={text} onChange={(e) => setText(e.target.value)} placeholder="Buscar na atividade…" />
        </label>
        {campaignId && (
          <select aria-label="Ficha" className="field" value={sheetFilter} onChange={(e) => setSheetFilter(e.target.value)}>
            <option value="">Todas as fichas</option>
            {sheets.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        )}
        <select aria-label="Categoria" className="field" value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">Categoria</option>
          {Object.entries(CATEGORIES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <input aria-label="Ator" className="field" style={{ flex: '0 1 160px', margin: 0 }} value={actor} onChange={(e) => setActor(e.target.value)} placeholder="Ator" />
        <span className="feed-count">{list.length} registro{list.length === 1 ? '' : 's'}</span>
      </div>
      {error && <p role="alert" className="text-sm" style={{ color: 'var(--accent)' }}>{error}</p>}
      {loading ? <div className="flex flex-col gap-3">{[0, 1, 2].map((i) => <div key={i} className="skeleton h-16 w-full" />)}</div> : <Timeline entries={list} />}
    </div>
  );
}
