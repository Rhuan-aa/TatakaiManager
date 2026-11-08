import { useCallback, useEffect, useRef, useState } from 'react';
import { useCampaignTopic } from '../../hooks/useWebSocket';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../contexts/ToastContext';
import { parseApiError } from '../../api/parseApiError';
import { listSheets } from '../../api/characterSheets';
import * as api from '../../api/combat';
import { EmptyState } from '../layout/AppShell';
import InitiativeTracker from './InitiativeTracker';
import ActionOverlay from './ActionOverlay';
import TurnQueue from './TurnQueue';
import AttackPanel from './AttackPanel';
import Timeline from './Timeline';
import { useRevealBusy } from '../rolls/revealQueue';
import ReactionOverlay from './ReactionOverlay';
import { playAttack } from './attackFx';
import Icon from '../ui/Icon';
import ConditionFx from '../ui/ConditionFx';
import { STATUS_LABEL, CLASS_LABEL, CLASS_COLOR, FLOOR_LABEL, FLOOR_COLOR } from './combatLabels';

const PREF_KEY = 'combat.autoActions';
// Preferência por usuário (neste navegador): quando o pop-up de ações abre sozinho
function readPref(isMaster) {
  const valid = isMaster ? ['all', 'npc', 'manual'] : ['auto', 'manual'];
  try {
    const v = localStorage.getItem(PREF_KEY);
    if (valid.includes(v)) return v;
  } catch { /* sem storage */ }
  return isMaster ? 'npc' : 'auto';
}

/** Cartão de seleção no estilo da classificação da luta (`--cls` = cor); sem permissão, só mostra o valor. */
function CardSelect({ color, title, label, value, options, prefix = '', editable, onChange, className = '' }) {
  return (
    <span className={`class-card ${className}`} style={{ '--cls': color }} title={title}>
      {editable ? (
        <>
          <select aria-label={label} value={value} onChange={(e) => onChange(e.target.value)}>
            {Object.entries(options).map(([k, l]) => <option key={k} value={k}>{prefix}{l}</option>)}
          </select>
          <span aria-hidden="true">▾</span>
        </>
      ) : <>{prefix}{options[value]}</>}
    </span>
  );
}


export default function CombatPanel({ campaignId, isMaster, npcs, onCloseFight }) {
  const { user } = useAuth();
  const toast = useToast();
  const [encounters, setEncounters] = useState([]);
  const [eid, setEid] = useState(null);
  const [enc, setEnc] = useState(null);
  const [events, setEvents] = useState([]);
  const [sheets, setSheets] = useState([]);
  const [name, setName] = useState('');
  const [defClass, setDefClass] = useState('REAL');
  const [pickFor, setPickFor] = useState(null);
  const [addSheet, setAddSheet] = useState('');
  const [addTeam, setAddTeam] = useState('');
  const [attacks, setAttacks] = useState([]);
  const [fx, setFx] = useState([]); // ataques recém-resolvidos a animar
  const seenAttacks = useRef({ eid: null, ids: new Set() });
  const seenLate = useRef({ eid: null, ids: new Set() }); // rolagens de contra-ataque tardio já vistas
  const [freshRolls, setFreshRolls] = useState(() => new Set());
  const [lateFx, setLateFx] = useState([]); // contra-ataques tardios recém-resolvidos (ataque sintético p/ attackFx)
  const [actionsFor, setActionsFor] = useState(null); // chave do turno em que o pop-up de ações está aberto
  const autoKey = useRef(null);
  const [pref, setPref] = useState(() => readPref(isMaster));
  const [minimized, setMinimized] = useState(() => new Set()); // alvos cuja tela de reação foi adiada

  const fail = useCallback((err) => toast(parseApiError(err).message, 'error'), [toast]);

  const reloadList = useCallback(async () => {
    try {
      const list = await api.listEncounters(campaignId);
      setEncounters(list);
      setEid((cur) => cur ?? (list.find((e) => e.status !== 'ENDED') ?? list[0])?.id ?? null);
    } catch (err) { fail(err); }
  }, [campaignId, fail]);

  const reload = useCallback(async (id) => {
    if (!id) return;
    try {
      const [e, ev] = await Promise.all([api.getEncounter(id), api.getEncounterEvents(id)]);
      setEnc(e);
      setEvents(ev);
      const list = await api.listAttacks(id);
      // ataques que acabaram de resolver: animam (na 1ª carga do encontro, nada anima)
      const known = seenAttacks.current;
      const resolved = list.filter((a) => a.status === 'RESOLVED');
      setFx(known.eid === id ? resolved.filter((a) => !known.ids.has(a.id)) : []);
      seenAttacks.current = { eid: id, ids: new Set(resolved.map((a) => a.id)) };
      // contra-ataques tardios que acabaram de resolver: rolagem animada + golpe sintético do alvo no atacante
      const lateKnown = seenLate.current;
      const lates = resolved.flatMap((a) => a.targets.filter((t) => t.lateCounterRoll).map((t) => ({ a, t })));
      const newLates = lateKnown.eid === id ? lates.filter(({ t }) => !lateKnown.ids.has(t.lateCounterRoll.id)) : [];
      seenLate.current = { eid: id, ids: new Set(lates.map(({ t }) => t.lateCounterRoll.id)) };
      setFreshRolls(new Set(newLates.map(({ t }) => t.lateCounterRoll.id)));
      setLateFx(newLates.map(({ a, t }) => ({
        attackerId: t.targetId,
        targets: [{ targetId: a.attackerId, outcome: t.lateCounter === 'SUCCESS' ? 'HIT' : 'MISS', damage: t.lateCounterDamage }],
      })));
      setAttacks(list);
    } catch (err) { fail(err); }
  }, [fail]);

  useEffect(() => { reloadList(); }, [reloadList]);
  // efeito do pai roda depois dos filhos: as rolagens (RollReveal) já entraram na fila antes do golpe
  useEffect(() => { fx.forEach(playAttack); }, [fx]);
  useEffect(() => { lateFx.forEach(playAttack); }, [lateFx]);
  useEffect(() => { setEnc(null); reload(eid); }, [eid, reload]);
  useEffect(() => {
    if (!isMaster) return;
    listSheets(campaignId).then(setSheets).catch(() => {});
  }, [campaignId, isMaster]);

  const onMessage = useCallback(() => { reload(eid); reloadList(); }, [eid, reload, reloadList]);
  useCampaignTopic(campaignId, 'combat', onMessage);

  // executa mutação, mostra erro e refaz o snapshot (o WS também faz, é idempotente)
  async function run(fn) {
    try { await fn(); await reload(eid); return true; } catch (err) { fail(err); return false; }
  }

  async function create(e) {
    e.preventDefault();
    try {
      const c = await api.createEncounter(campaignId, name.trim(), defClass);
      setName('');
      await reloadList();
      setEid(c.id);
    } catch (err) { fail(err); }
  }

  const inEnc = new Set(enc?.participants.map((p) => p.sheetId));
  const candidates = [
    ...sheets.filter((s) => s.buildStatus === 'COMPLETED').map((s) => ({ id: s.id, label: `${s.name} (${s.ownerName})` })),
    ...npcs.filter((n) => n.sheetId && n.combatReady).map((n) => ({ id: n.sheetId, label: `${n.name} (NPC)` })),
  ].filter((c) => !inEnc.has(c.id));

  const current = enc?.participants.find((p) => p.id === enc.currentParticipantId);
  const isOwner = (p) => p?.ownerUserId && p.ownerUserId === user?.userId;
  const controlsCurrent = enc?.status === 'ACTIVE' && !!current && (isMaster || isOwner(current));
  const canAct = controlsCurrent;
  // alvos pendentes que esperam a minha reação (os adiados saem da fila até eu reabrir)
  const respondable = attacks.filter((a) => a.status === 'AWAITING_REACTIONS')
    .flatMap((a) => a.targets.map((t) => ({ a, t })))
    .filter(({ t }) => t.canRespond && !t.reaction && (t.options || t.guardians));
  const queue = respondable.filter(({ t }) => !minimized.has(t.id));
  const awaiting = queue[0];
  const awaitingId = awaiting?.t.id;
  const deferred = respondable.length - queue.length;
  const minimize = useCallback(() => {
    if (awaitingId) setMinimized((m) => new Set(m).add(awaitingId));
  }, [awaitingId]);
  const pendingIds = new Set(attacks.filter((a) => a.status === 'AWAITING_REACTIONS')
    .flatMap((a) => a.targets.filter((t) => !t.reaction).map((t) => t.targetId)));

  // pop-up de ações: aberto para a chave do turno atual; abre sozinho (1x por turno) conforme a preferência
  const turnKey = enc ? `${enc.turnCounter ?? enc.round}:${enc.currentParticipantId}` : null;
  const revealing = useRevealBusy(); // rolagem animando: o menu de ações se minimiza
  const actionsOpen = controlsCurrent && actionsFor === turnKey && !revealing;
  const closeActions = useCallback(() => setActionsFor(null), []);
  const changePref = (v) => {
    setPref(v);
    try { localStorage.setItem(PREF_KEY, v); } catch { /* sem storage */ }
  };
  useEffect(() => {
    if (!controlsCurrent || autoKey.current === turnKey) return;
    autoKey.current = turnKey;
    const auto = isMaster ? pref === 'all' || (pref === 'npc' && current?.npc) : pref === 'auto';
    if (auto) setActionsFor(turnKey);
  }, [controlsCurrent, turnKey, pref, isMaster, current?.npc]);

  return (
    <div className={`space-y-4 ${enc?.status === 'ACTIVE' ? 'pb-24' : ''}`}>
      <div className="flex flex-wrap items-center gap-2">
        {encounters.length > 0 && (
          <select className="field !w-auto" value={eid ?? ''} onChange={(e) => setEid(e.target.value)} aria-label="Encontro">
            {encounters.map((x) => <option key={x.id} value={x.id}>{x.name} — {STATUS_LABEL[x.status]}</option>)}
          </select>
        )}
        {isMaster && (
          <form onSubmit={create} className="flex gap-2">
            <input className="field" required maxLength={100} placeholder="Novo encontro" value={name}
              onChange={(e) => setName(e.target.value)} />
            <select className="field !w-auto" aria-label="Classificação padrão" value={defClass} onChange={(e) => setDefClass(e.target.value)}>
              {Object.entries(CLASS_LABEL).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
            </select>
            <button className="btn-primary" type="submit">Criar</button>
          </form>
        )}
      </div>

      {!enc && <EmptyState title="Nenhum encontro" description={isMaster ? 'Crie um encontro para começar.' : 'O Mestre ainda não abriu um encontro.'} />}

      {enc && (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <span className="tag">{STATUS_LABEL[enc.status]}</span>
            {enc.status === 'ENDED' && (
              <span className="caption">
                Última luta{enc.endedAt ? ` — encerrada em ${new Date(enc.endedAt).toLocaleString('pt-BR')}` : ''}, rodada {enc.round}
              </span>
            )}
            {isMaster && enc.status === 'SETUP' && (
              <button className="btn-primary" disabled={enc.participants.length === 0}
                onClick={() => run(() => api.startEncounter(enc.id))}>Iniciar</button>
            )}
            {enc.status === 'ACTIVE' && (isMaster || isOwner(current)) && (
              <button type="button" className="btn-next-turn" onClick={() => run(() => api.nextTurn(enc.id))}>
                Avançar turno ▶
              </button>
            )}
            {controlsCurrent && (
              // fixo ao lado do "Avançar turno" (que ocupa o canto inferior direito)
              <button type="button" className="btn-primary fixed bottom-6 right-[17rem] z-50 flex items-center gap-2"
                aria-haspopup="dialog" onClick={() => setActionsFor(turnKey)}>
                <Icon name="swords" size={18} />Ações ({current.paCurrent} PA)
              </button>
            )}
            {deferred > 0 && (
              <button type="button" className="btn-secondary fixed bottom-24 right-6 z-50 flex items-center gap-2"
                onClick={() => setMinimized(new Set())}>
                <Icon name="swords" size={18} />Reações pendentes ({deferred})
              </button>
            )}
            {isMaster && enc.status === 'ENDED' && onCloseFight && (
              <button className="btn-primary" onClick={() => onCloseFight(enc.participants.filter((p) => !p.npc).map((p) => p.sheetId))}>
                Fechar luta
              </button>
            )}
            {isMaster && enc.status !== 'ENDED' && (
              <button className="btn-ghost" onClick={() => window.confirm('Encerrar o encontro?') && run(() => api.endEncounter(enc.id))}>Encerrar</button>
            )}
            {(() => {
              const floor = enc.floor ?? 'STANDARD';
              const cls = enc.defaultClassification ?? 'REAL';
              const canEdit = isMaster && enc.status !== 'ENDED';
              return (
                <>
                  <CardSelect color={FLOOR_COLOR[floor]} title="Chão do encontro. Dano de Arremesso: macio +0, padrão +1d4, duro +2d4 e 1 PQ"
                    label="Chão do encontro" className="ml-auto" value={floor} options={FLOOR_LABEL} prefix="Chão " editable={canEdit}
                    onChange={(v) => run(() => api.updateEncounter(enc.id, { floor: v }))} />
                  <CardSelect color={CLASS_COLOR[cls]} title="Classificação da luta"
                    label="Classificação da luta" value={cls} options={CLASS_LABEL} prefix="Luta " editable={canEdit}
                    onChange={(v) => run(() => api.updateEncounter(enc.id, { defaultClassification: v }))} />
                </>
              );
            })()}
          </div>

          {isMaster && enc.status !== 'ENDED' && (
            <form className="flex gap-2" onSubmit={(e) => {
              e.preventDefault();
              if (addSheet) run(() => api.addParticipant(enc.id, addSheet, Number(addTeam) || undefined)).then(() => setAddSheet(''));
            }}>
              <select className="field !w-auto" value={addSheet} onChange={(e) => setAddSheet(e.target.value)} aria-label="Participante">
                <option value="">Adicionar participante…</option>
                {candidates.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
              </select>
              <label className="flex items-center gap-1 text-xs font-bold uppercase">Time
                <input type="number" min={1} max={9} className="field !mt-0 !w-16 !py-1" value={addTeam} placeholder="auto"
                  aria-label="Time do participante (vazio: jogador 1, NPC 2)" onChange={(e) => setAddTeam(e.target.value)} />
              </label>
              <button className="btn-secondary" type="submit" disabled={!addSheet}>Adicionar</button>
            </form>
          )}

          {enc.status === 'ACTIVE' && current && (
            <div className="panel sticky top-2 z-30 flex flex-wrap items-center gap-x-4 gap-y-2 px-3 py-2">
              <span className="tag tag-accent">Rodada {enc.round}</span>
              <span className="panel-title text-xl" aria-live="polite">Turno de {current.name}{isOwner(current) ? ' (você)' : ''}</span>
              <span className="flex items-center gap-1.5" aria-label={`${current.paCurrent} PA`} title={`${current.paCurrent} PA`}>
                {Array.from({ length: Math.max(5, current.paCurrent) }, (_, i) => (
                  <span key={i} className={`pa-pip${i < current.paCurrent ? ' on' : ''}`} />
                ))}
                <span className="ml-1 text-sm font-bold">{current.paCurrent} PA</span>
              </span>
              {current.resources?.currentSp != null && <span className="text-sm font-bold">{current.resources.currentSp} SP</span>}
              <div className="w-full"><TurnQueue enc={enc} pendingIds={pendingIds} /></div>
            </div>
          )}

          <div className="min-w-0 space-y-4">
            <AttackPanel
              freshIds={new Set(fx.map((a) => a.id))}
              freshRolls={freshRolls}
              pending={attacks.filter((a) => a.status === 'AWAITING_REACTIONS')}
              resolved={attacks.filter((a) => a.status === 'RESOLVED').slice(0, 5)}
              canCancel={(a) => {
                if (a.status !== 'AWAITING_REACTIONS') return false;
                const att = enc.participants.find((x) => x.id === a.attackerId);
                return isMaster || isOwner(att);
              }}
              onReact={(aid, tid, r, did, tech) => run(() => api.respondReaction(aid, tid, r, did, tech))}
              onCancel={(aid) => run(() => api.cancelAttack(aid))}
              onLateCounter={(aid, tid, b) => run(() => api.lateCounter(aid, tid, b))}
              targetSheetId={(t) => enc.participants.find((x) => x.id === (t.targetId))?.sheetId}
              attackerSheetId={(a) => enc.participants.find((x) => x.id === a.attackerId)?.sheetId}
              onChain={(aid, tid, v) => run(() => api.chainAttack(aid, tid, v))} />

            {enc.participants.length === 0
              ? <p className="caption">Sem participantes.</p>
              : <InitiativeTracker enc={enc} isMaster={isMaster} canControl={(p) => isMaster || isOwner(p)}
                  onHysteria={(pid, h) => run(() => api.setHysteria(enc.id, pid, h))}
                  onVitals={(pid, b) => run(() => api.adjustVitals(enc.id, pid, b))}
                  onAdjust={(pid, b) => run(() => api.adjustParticipant(enc.id, pid, b))}
                  onReroll={(pid) => run(() => api.rerollInitiative(enc.id, pid))}
                  onRemove={(pid) => run(() => api.removeParticipant(enc.id, pid))}
                  onRemoveCondition={(cid) => run(() => api.removeCondition(enc.id, cid))}
                  onCounter={(id, v) => run(() => api.setCounter(enc.id, id, v))}
                  onPickCondition={setPickFor} pickFor={pickFor} onCancelCondition={() => setPickFor(null)}
                  onSubmitCondition={(pid, body) => run(() => api.addCondition(enc.id, pid, body)).then(() => setPickFor(null))} />}
            <ConditionFx key={enc.id} entries={enc.participants.flatMap((p) => [
              ...(p.conditions ?? []).map((c) => ({ id: `${c.id}:${c.intensity ?? ''}`, anchorId: p.id, key: c.key,
                name: c.intensity ? `${c.name} ${c.intensity}` : c.name })),
              ...(p.openWounds > 0 ? [{ id: `${p.id}:w${p.openWounds}`, anchorId: p.id, key: 'FERIDA', name: `Feridas ${p.openWounds}` }] : []),
            ])} />
          </div>

          <Timeline events={events} participants={enc.participants} />

          {controlsCurrent && (
            <ActionOverlay key={turnKey} open={actionsOpen} suspended={!!awaiting} participant={current}
              participants={enc.participants} canAct={canAct} isMaster={isMaster} pref={pref} onPref={changePref}
              onAct={(b) => run(() => api.doAction(enc.id, b))}
              onGrapple={(b) => run(() => api.grapple(enc.id, b))}
              onDeclare={(b) => run(() => api.declareAttack(enc.id, b))}
              onClose={closeActions} />
          )}

          {awaiting && (
            <ReactionOverlay attack={awaiting.a} target={awaiting.t} position={1} total={queue.length}
              onReact={(aid, tid, r, did, tech) => run(() => api.respondReaction(aid, tid, r, did, tech))}
              onMinimize={minimize} />
          )}
        </>
      )}
    </div>
  );
}
