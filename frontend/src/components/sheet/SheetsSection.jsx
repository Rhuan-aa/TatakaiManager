import { useCallback, useEffect, useRef, useState } from 'react';
import { listSheets, getSheet } from '../../api/characterSheets';
import { listRules } from '../../api/campaignRules';
import { startBuild } from '../../api/characterBuild';
import BuildWizard from '../build/BuildWizard';
import { parseApiError } from '../../api/parseApiError';
import { useAuth } from '../../contexts/AuthContext';
import { useCampaignTopic } from '../../hooks/useWebSocket';
import { EmptyState } from '../layout/AppShell';
import CharacterSheetForm from './CharacterSheetForm';
import CharacterSheetView from './CharacterSheetView';
import ActivityLogPanel from './ActivityLogPanel';

const STATUS = { DRAFT: 'Rascunho', PENDING_APPROVAL: 'Aguardando aprovação', COMPLETED: 'Aprovada', IN_BUILD: 'Em construção' };

export default function SheetsSection({ campaignId, isMaster }) {
  const { user } = useAuth();
  const [sheets, setSheets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [openId, setOpenId] = useState(null);
  const [open, setOpen] = useState(null);
  const [creating, setCreating] = useState(false);
  const [free, setFree] = useState(false);
  const [newName, setNewName] = useState('');
  const [building, setBuilding] = useState(null);
  const [tab, setTab] = useState('sheets');
  const [tick, setTick] = useState(0);
  const openIdRef = useRef(null);
  openIdRef.current = openId;

  const reload = useCallback(async () => {
    try {
      setSheets(await listSheets(campaignId));
      setError('');
      if (openIdRef.current) setOpen(await getSheet(openIdRef.current));
      setTick((t) => t + 1);
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setLoading(false);
    }
  }, [campaignId]);

  useEffect(() => {
    let active = true;
    listSheets(campaignId)
      .then((d) => active && setSheets(d))
      .catch((e) => active && setError(parseApiError(e).message))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [campaignId]);

  useEffect(() => {
    let active = true;
    listRules(campaignId)
      .then((d) => active && setFree(d.some((r) => r.ruleKey === 'FREE_CREATION' && r.enabled)))
      .catch(() => {});
    return () => { active = false; };
  }, [campaignId]);

  async function begin(e) {
    e.preventDefault();
    try {
      const b = await startBuild(campaignId, { name: newName.trim() });
      setCreating(false);
      setNewName('');
      await reload();
      setBuilding(b.sheetId);
    } catch (err) {
      setError(parseApiError(err).message);
    }
  }

  useEffect(() => {
    if (!openId) { setOpen(null); return undefined; }
    let active = true;
    getSheet(openId)
      .then((d) => active && setOpen(d))
      .catch((e) => active && setError(parseApiError(e).message));
    return () => {
      active = false;
    };
  }, [openId]);

  useCampaignTopic(campaignId, 'sheets', reload);

  const mine = sheets.find((s) => s.ownerUserId === user?.userId);

  if (loading) return <div className="skeleton h-32 w-full" />;

  if (building) {
    return (
      <BuildWizard
        campaignId={campaignId}
        sheetId={building}
        isMaster={isMaster}
        onDone={() => { const id = building; setBuilding(null); setOpenId(id); reload(); }}
      />
    );
  }

  if (creating && !free) {
    return (
      <form onSubmit={begin} className="panel flex flex-col gap-3 p-4">
        <h2 className="panel-title">Criar minha ficha</h2>
        <label className="text-xs uppercase">
          Nome do personagem
          <input required maxLength={100} className="field mt-1 w-full" value={newName} onChange={(e) => setNewName(e.target.value)} />
        </label>
        <div className="flex gap-2">
          <button type="submit" className="btn-primary" disabled={!newName.trim()}>Iniciar construção</button>
          <button type="button" className="btn-ghost" onClick={() => setCreating(false)}>Cancelar</button>
        </div>
      </form>
    );
  }

  if (creating) {
    return (
      <div className="panel p-4">
        <h2 className="panel-title mb-3">Criar minha ficha</h2>
        <CharacterSheetForm
          campaignId={campaignId}
          onSaved={(s) => { setCreating(false); setOpenId(s.id); reload(); }}
          onCancel={() => setCreating(false)}
        />
      </div>
    );
  }

  if (openId && open?.buildStatus === 'IN_BUILD') {
    return <BuildWizard campaignId={campaignId} sheetId={openId} isMaster={isMaster} onDone={() => { reload(); }} />;
  }

  if (openId) {
    return open ? (
      <CharacterSheetView
        sheet={open}
        isMaster={isMaster}
        campaignId={campaignId}
        refreshKey={tick}
        onChange={(s) => { setOpen(s); reload(); }}
        onBack={() => setOpenId(null)}
      />
    ) : <div className="skeleton h-32 w-full" />;
  }

  return (
    <div className="flex flex-col gap-4">
      {error && <p role="alert" className="text-sm" style={{ color: 'var(--accent)' }}>{error}</p>}
      <div className="flex flex-wrap items-center gap-2">
        {isMaster && (
          <>
            <button type="button" className={tab === 'sheets' ? 'btn-primary' : 'btn-secondary'} onClick={() => setTab('sheets')}>Fichas</button>
            <button type="button" className={tab === 'log' ? 'btn-primary' : 'btn-secondary'} onClick={() => setTab('log')}>Log de atividade</button>
          </>
        )}
        {!isMaster && !mine && (
          <button type="button" className="btn-primary" onClick={() => setCreating(true)}>Criar minha ficha</button>
        )}
      </div>

      {tab === 'log' && isMaster ? (
        <ActivityLogPanel campaignId={campaignId} sheets={sheets} refreshKey={tick} />
      ) : sheets.length === 0 ? (
        <EmptyState title="Nenhuma ficha ainda" description="As fichas dos jogadores aparecerão aqui." />
      ) : (
        <div className="page-gutter grid sm:grid-cols-2 lg:grid-cols-3">
          {sheets.map((s) => (
            <button key={s.id} type="button" onClick={() => setOpenId(s.id)}
              className="panel p-4 text-left focus-visible:outline focus-visible:outline-[3px] focus-visible:outline-[var(--accent)]">
              <div className="panel-title">{s.name}</div>
              <div className="text-sm">{s.ownerName}</div>
              <div className="mt-2 flex flex-wrap gap-2">
                <span className="tag">{STATUS[s.buildStatus]}</span>
                {s.criticalState && <span className="tag-accent tag">Crítico</span>}
              </div>
              {s.effectiveHpMax != null && (
                <div className="mt-2 text-xs">HP {s.currentHp}/{s.effectiveHpMax} · SP {s.currentSp}/{s.spMax} · PQ {s.currentKp}/{s.kpMax}</div>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
