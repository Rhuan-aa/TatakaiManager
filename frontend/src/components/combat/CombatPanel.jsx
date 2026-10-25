import { useCallback, useEffect, useState } from 'react';
import { useCampaignTopic } from '../../hooks/useWebSocket';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../contexts/ToastContext';
import { parseApiError } from '../../api/parseApiError';
import { listSheets } from '../../api/characterSheets';
import * as api from '../../api/combat';
import { EmptyState } from '../layout/AppShell';
import InitiativeTracker from './InitiativeTracker';
import ActionBar from './ActionBar';
import ConditionPicker from './ConditionPicker';
import AttackBuilder from './AttackBuilder';
import AttackPanel from './AttackPanel';
import Timeline from './Timeline';
import ReactionOverlay from './ReactionOverlay';
import { STATUS_LABEL, CLASS_LABEL } from './combatLabels';

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
  const [attacks, setAttacks] = useState([]);
  const [attacking, setAttacking] = useState(null); // null | 'MAJOR' | 'MINOR_OFFENSIVE'
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
      setAttacks(await api.listAttacks(id));
    } catch (err) { fail(err); }
  }, [fail]);

  useEffect(() => { reloadList(); }, [reloadList]);
  useEffect(() => { setEnc(null); reload(eid); }, [eid, reload]);
  useEffect(() => {
    if (!isMaster) return;
    listSheets(campaignId).then(setSheets).catch(() => {});
  }, [campaignId, isMaster]);

  const onMessage = useCallback(() => { reload(eid); reloadList(); }, [eid, reload, reloadList]);
  useCampaignTopic(campaignId, 'combat', onMessage);

  // executa mutação, mostra erro e refaz o snapshot (o WS também faz, é idempotente)
  async function run(fn) {
    try { await fn(); await reload(eid); } catch (err) { fail(err); }
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
    ...npcs.filter((n) => n.sheetId).map((n) => ({ id: n.sheetId, label: `${n.name} (NPC)` })),
  ].filter((c) => !inEnc.has(c.id));

  const current = enc?.participants.find((p) => p.id === enc.currentParticipantId);
  const isOwner = (p) => p?.ownerUserId && p.ownerUserId === user?.userId;
  const controlsCurrent = enc?.status === 'ACTIVE' && !!current && (isMaster || isOwner(current));
  const canAct = controlsCurrent;
  // primeiro alvo pendente que espera a minha reação (e que não adiei)
  const awaiting = attacks.filter((a) => a.status === 'AWAITING_REACTIONS')
    .flatMap((a) => a.targets.map((t) => ({ a, t })))
    .find(({ t }) => t.canRespond && !t.reaction && t.options && !minimized.has(t.id));
  const awaitingId = awaiting?.t.id;
  const minimize = useCallback(() => {
    if (awaitingId) setMinimized((m) => new Set(m).add(awaitingId));
  }, [awaitingId]);

  return (
    <div className="space-y-4">
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
              <button className="btn-secondary" onClick={() => run(() => api.nextTurn(enc.id))}>Avançar turno</button>
            )}
            {isMaster && enc.status !== 'ENDED' && (
              <label className="flex items-center gap-1 text-xs">Classificação padrão
                <select className="field !w-auto !py-0.5" value={enc.defaultClassification ?? 'REAL'}
                  onChange={(e) => run(() => api.updateEncounter(enc.id, { defaultClassification: e.target.value }))}>
                  {Object.entries(CLASS_LABEL).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                </select>
              </label>
            )}
            {isMaster && enc.status === 'ENDED' && onCloseFight && (
              <button className="btn-primary" onClick={() => onCloseFight(enc.participants.filter((p) => !p.npc).map((p) => p.sheetId))}>
                Fechar luta
              </button>
            )}
            {isMaster && enc.status !== 'ENDED' && (
              <button className="btn-ghost" onClick={() => window.confirm('Encerrar o encontro?') && run(() => api.endEncounter(enc.id))}>Encerrar</button>
            )}
          </div>

          {isMaster && enc.status !== 'ENDED' && (
            <form className="flex gap-2" onSubmit={(e) => {
              e.preventDefault();
              if (addSheet) run(() => api.addParticipant(enc.id, addSheet)).then(() => setAddSheet(''));
            }}>
              <select className="field !w-auto" value={addSheet} onChange={(e) => setAddSheet(e.target.value)} aria-label="Participante">
                <option value="">Adicionar participante…</option>
                {candidates.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
              </select>
              <button className="btn-secondary" type="submit" disabled={!addSheet}>Adicionar</button>
            </form>
          )}

          {pickFor && (
            <ConditionPicker participant={pickFor} participants={enc.participants}
              onCancel={() => setPickFor(null)}
              onSubmit={(pid, body) => run(() => api.addCondition(enc.id, pid, body)).then(() => setPickFor(null))} />
          )}

          {enc.status === 'ACTIVE' && current && (
            <div className="panel flex flex-wrap items-center gap-x-4 gap-y-2 px-3 py-2" aria-live="polite">
              <span className="tag tag-accent">Rodada {enc.round}</span>
              <span className="panel-title text-xl">Turno de {current.name}{isOwner(current) ? ' (você)' : ''}</span>
              <span className="flex items-center gap-1.5" aria-label={`${current.paCurrent} PA`} title={`${current.paCurrent} PA`}>
                {Array.from({ length: Math.max(5, current.paCurrent) }, (_, i) => (
                  <span key={i} className={`pa-pip${i < current.paCurrent ? ' on' : ''}`} />
                ))}
                <span className="ml-1 text-sm font-bold">{current.paCurrent} PA</span>
              </span>
              {current.resources?.currentSp != null && <span className="text-sm font-bold">{current.resources.currentSp} SP</span>}
            </div>
          )}

          <div className={`grid gap-4 ${controlsCurrent ? 'lg:grid-cols-[18rem_1fr]' : ''}`}>
            {controlsCurrent && (
              <aside className="lg:sticky lg:top-4 lg:self-start">
                <ActionBar participant={current} participants={enc.participants} canAct={canAct}
                  onAct={(b) => run(() => api.doAction(enc.id, b))}
                  onAttack={(kind) => setAttacking(kind)} />
              </aside>
            )}
            <div className="min-w-0 space-y-4">
              {attacking && canAct && (
                <AttackBuilder key={attacking} participant={current} participants={enc.participants} initialKind={attacking}
                  onCancel={() => setAttacking(null)}
                  onSubmit={(b) => run(() => api.declareAttack(enc.id, b)).then(() => setAttacking(null))} />
              )}

              <AttackPanel
                pending={attacks.filter((a) => a.status === 'AWAITING_REACTIONS')}
                resolved={attacks.filter((a) => a.status === 'RESOLVED').slice(0, 5)}
                canCancel={(a) => {
                  if (a.status !== 'AWAITING_REACTIONS') return false;
                  const att = enc.participants.find((x) => x.id === a.attackerId);
                  return isMaster || isOwner(att);
                }}
                onReact={(aid, tid, r) => run(() => api.respondReaction(aid, tid, r))}
                onCancel={(aid) => run(() => api.cancelAttack(aid))}
                onLateCounter={(aid, tid, b) => run(() => api.lateCounter(aid, tid, b))}
                targetSheetId={(t) => enc.participants.find((x) => x.id === (t.targetId))?.sheetId} />

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
                    onPickCondition={setPickFor} />}
            </div>
          </div>

          <section>
            <h3 className="panel-title mb-2 text-lg">Linha do tempo</h3>
            <Timeline events={events} />
          </section>

          {awaiting && (
            <ReactionOverlay attack={awaiting.a} target={awaiting.t}
              onReact={(aid, tid, r) => run(() => api.respondReaction(aid, tid, r))}
              onMinimize={minimize} />
          )}
        </>
      )}
    </div>
  );
}
