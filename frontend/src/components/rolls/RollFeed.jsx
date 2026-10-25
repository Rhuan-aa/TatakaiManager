import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  createRollChannel, deleteRollChannel, gmRoll, listCampaignRolls, listRollChannels, revealRoll, updateRollChannel,
} from '../../api/rolls';
import { listSheets } from '../../api/characterSheets';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { useCampaignTopic, useStompDestination } from '../../hooks/useWebSocket';
import { DICE_REGEX } from '../sheet/sheetRules';
import { RollRow } from './RollCard';
import RollReveal from './RollReveal';

const upsert = (list = [], r) => [r, ...list.filter((x) => x.id !== r.id)].slice(0, 100);

function GmRollForm({ campaignId, tab }) {
  const toast = useToast();
  const [expression, setExpression] = useState('1d20');
  const [purpose, setPurpose] = useState('');
  const [busy, setBusy] = useState(false);
  const ok = DICE_REGEX.test(expression.trim()) && purpose.trim() && !busy;
  const channel = tab === 'PUBLIC' || tab === 'GM_PRIVATE' ? tab : 'CUSTOM';

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    try {
      await gmRoll(campaignId, {
        expression: expression.trim(), purpose: purpose.trim(), channel,
        customChannelId: channel === 'CUSTOM' ? tab : undefined,
      });
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="panel grid gap-2 p-3 sm:grid-cols-[7rem_1fr_auto] sm:items-end">
      <label className="text-xs uppercase">Expressão
        <input className="field mt-1 w-full" value={expression} onChange={(e) => setExpression(e.target.value)} />
      </label>
      <label className="text-xs uppercase">Propósito
        <input className="field mt-1 w-full" value={purpose} maxLength={200} onChange={(e) => setPurpose(e.target.value)} />
      </label>
      <button type="submit" className="btn-primary" disabled={!ok}>{busy ? 'Rolando…' : 'Rolar'}</button>
    </form>
  );
}

/** Criar/editar canal: nome + jogadores da campanha (derivados das fichas). */
function ChannelForm({ campaignId, channel, onDone, onCancel }) {
  const toast = useToast();
  const [name, setName] = useState(channel?.name ?? '');
  const [ids, setIds] = useState(new Set(channel?.memberIds ?? []));
  const [players, setPlayers] = useState([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    listSheets(campaignId).then((sheets) => {
      if (!active) return;
      const m = new Map();
      sheets.filter((s) => s.ownerUserId && !s.npcId).forEach((s) => m.set(s.ownerUserId, s.ownerName));
      setPlayers([...m].map(([id, n]) => ({ id, name: n })));
    }).catch(() => {});
    return () => { active = false; };
  }, [campaignId]);

  const toggle = (id) => setIds((s) => { const n = new Set(s); if (n.has(id)) n.delete(id); else n.add(id); return n; });

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    try {
      const body = { name: name.trim(), memberIds: [...ids] };
      onDone(channel ? await updateRollChannel(campaignId, channel.id, body) : await createRollChannel(campaignId, body));
    } catch (err) {
      toast(parseApiError(err).message, 'error');
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="panel flex flex-col gap-2 p-3">
      <label className="text-xs uppercase">Nome do canal
        <input className="field mt-1 w-full max-w-sm" value={name} maxLength={50} pattern="[^<>]*" onChange={(e) => setName(e.target.value)} />
      </label>
      <fieldset className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
        <legend className="text-xs uppercase">Jogadores</legend>
        {players.length === 0 && <span className="caption">Nenhum jogador com ficha.</span>}
        {players.map((p) => (
          <label key={p.id}><input type="checkbox" checked={ids.has(p.id)} onChange={() => toggle(p.id)} /> {p.name}</label>
        ))}
      </fieldset>
      <div className="flex gap-2">
        <button type="submit" className="btn-primary" disabled={!name.trim() || busy}>{channel ? 'Salvar' : 'Criar canal'}</button>
        <button type="button" className="btn-ghost" onClick={onCancel}>Cancelar</button>
      </div>
    </form>
  );
}

export default function RollFeed({ campaignId, isMaster }) {
  const toast = useToast();
  const [channels, setChannels] = useState([]);
  const [tab, setTab] = useState('PUBLIC');
  const [rolls, setRolls] = useState({});
  const [showHistory, setShowHistory] = useState(false);
  const [filter, setFilter] = useState('');
  const [form, setForm] = useState(null); // null | 'new' | channel
  const histIds = useRef(new Set()); // ids vindos do histórico: nunca animam

  useEffect(() => {
    let active = true;
    listRollChannels(campaignId).then((d) => active && setChannels(d)).catch(() => {});
    return () => { active = false; };
  }, [campaignId]);

  // Carrega o histórico da aba ao selecioná-la (uma vez).
  useEffect(() => {
    if (rolls[tab]) return;
    let active = true;
    const custom = tab !== 'PUBLIC' && tab !== 'GM_PRIVATE';
    listCampaignRolls(campaignId, custom ? 'CUSTOM' : tab, custom ? tab : undefined)
      .then((d) => { if (!active) return; d.forEach((r) => histIds.current.add(r.id)); setRolls((m) => ({ ...m, [tab]: m[tab] ? m[tab] : d })); })
      .catch((e) => toast(parseApiError(e).message, 'error'));
    return () => { active = false; };
  }, [campaignId, tab, rolls, toast]);

  const add = useCallback((key, r) => setRolls((m) => ({ ...m, [key]: upsert(m[key], r) })), []);
  const onPublic = useCallback((r) => add('PUBLIC', r), [add]);
  const onPrivate = useCallback((r) => add('GM_PRIVATE', r), [add]);
  const onCustom = useCallback((r) => r.customChannelId && add(r.customChannelId, r), [add]);
  useCampaignTopic(campaignId, 'rolls', onPublic);
  useStompDestination('/user/queue/gm-rolls', onPrivate, isMaster);
  useStompDestination('/user/queue/rolls', onCustom, isMaster || channels.length > 0);

  async function reveal(r) {
    try {
      const res = await revealRoll(campaignId, r.id);
      add(tab, { ...r, ...res, revealed: true });
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    }
  }

  async function remove(c) {
    if (!window.confirm(`Apagar o canal "${c.name}" e todas as suas rolagens?`)) return;
    try {
      await deleteRollChannel(campaignId, c.id);
      setChannels((l) => l.filter((x) => x.id !== c.id));
      setTab('PUBLIC');
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    }
  }

  function saved(c) {
    setChannels((l) => (l.some((x) => x.id === c.id) ? l.map((x) => (x.id === c.id ? c : x)) : [...l, c]));
    setForm(null);
    setTab(c.id);
  }

  const q = filter.trim().toLowerCase();
  const match = useMemo(
    () => (r) => !q || `${r.purpose ?? ''} ${r.characterName ?? ''} ${r.actorName ?? ''} ${r.sourceName ?? ''}`.toLowerCase().includes(q),
    [q],
  );
  const list = (rolls[tab] ?? []).filter(match);
  const [latest, ...history] = list;
  const current = channels.find((c) => c.id === tab);
  const canReveal = isMaster && tab !== 'PUBLIC';
  const tabs = [['PUBLIC', 'Público'], ...(isMaster ? [['GM_PRIVATE', 'Privado do Mestre']] : []), ...channels.map((c) => [c.id, c.name])];

  return (
    <div className="flex flex-col gap-4">
      <div role="tablist" aria-label="Canais de rolagem" className="flex flex-wrap items-center gap-2">
        {tabs.map(([k, l]) => (
          <button key={k} type="button" role="tab" aria-selected={tab === k} onClick={() => { setTab(k); setShowHistory(false); }}
            className={tab === k ? 'btn-primary text-xs' : 'btn-ghost text-xs'}>{l}</button>
        ))}
        {isMaster && <button type="button" className="btn-secondary text-xs" onClick={() => setForm('new')}>+ Canal</button>}
      </div>

      {form && (
        <ChannelForm key={form.id ?? 'new'} campaignId={campaignId} channel={form === 'new' ? undefined : form}
          onDone={saved} onCancel={() => setForm(null)} />
      )}
      {isMaster && current && !form && (
        <div className="flex gap-2">
          <button type="button" className="btn-ghost text-xs" onClick={() => setForm(current)}>Editar canal</button>
          <button type="button" className="btn-ghost text-xs" onClick={() => remove(current)}>Apagar canal</button>
        </div>
      )}

      {isMaster && <GmRollForm campaignId={campaignId} tab={tab} />}
      <input className="field w-full max-w-sm" placeholder="Filtrar por propósito ou personagem" value={filter}
        onChange={(e) => setFilter(e.target.value)} aria-label="Filtrar rolagens" />

      {!latest && <p className="caption">Nenhuma rolagem.</p>}
      {latest && (
        <RollReveal key={latest.id} roll={latest} fresh={!histIds.current.has(latest.id)} onReveal={canReveal && !latest.revealed ? () => reveal(latest) : undefined} />
      )}
      {history.length > 0 && (
        <div>
          <button type="button" className="text-xs underline" aria-expanded={showHistory} onClick={() => setShowHistory((s) => !s)}>
            Histórico ({history.length})
          </button>
          {showHistory && (
            <ul className="mt-2 flex flex-col">
              {history.map((r) => (
                <RollRow key={r.id} roll={r} onReveal={canReveal && !r.revealed ? () => reveal(r) : undefined} />
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
