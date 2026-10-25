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
  const [attacking, setAttacking] = useState(false);

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
  const canAct = enc?.status === 'ACTIVE' && !!current && (isMaster || isOwner(current));

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
            {enc.status === 'ACTIVE' && <span className="tag">Rodada {enc.round}</span>}
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

          {enc.status === 'ACTIVE' && current && (isMaster || isOwner(current)) && (
            <>
              <ActionBar participant={current} participants={enc.participants} canAct={canAct} onAct={(b) => run(() => api.doAction(enc.id, b))}
                onAttack={() => setAttacking((v) => !v)} />
              {attacking && canAct && (
                <AttackBuilder participant={current} participants={enc.participants} onCancel={() => setAttacking(false)}
                  onSubmit={(b) => run(() => api.declareAttack(enc.id, b)).then(() => setAttacking(false))} />
              )}
            </>
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

          <div className="grid gap-4 lg:grid-cols-[3fr_2fr]">
            <div>
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
            <div>
              <h3 className="panel-title mb-2 text-lg">Linha do tempo</h3>
              <Timeline events={events} />
            </div>
          </div>
        </>
      )}
    </div>
  );
}
