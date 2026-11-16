import { useCallback, useEffect, useRef, useState } from 'react';
import { listSheets, getSheet, importSheet } from '../../api/characterSheets';
import { listRules } from '../../api/campaignRules';
import { startBuild } from '../../api/characterBuild';
import BuildWizard from '../build/BuildWizard';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { useCampaignTopic } from '../../hooks/useWebSocket';
import { EmptyState } from '../layout/AppShell';
import CharacterSheetForm from './CharacterSheetForm';
import CharacterSheetView from './CharacterSheetView';
import ActivityLogPanel from './ActivityLogPanel';
import FightClosePanel from './FightClosePanel';
import GmSheets from './GmSheets';
import SheetCarousel from './SheetCarousel';
import { useAuth } from '../../contexts/AuthContext';

export default function SheetsSection({ campaignId, isMaster, fightSheetIds, npcs = [], setNpcs, onSheetOpen, focusSheetId, focusSection }) {
  const toast = useToast();
  const { user } = useAuth();
  const [warnings, setWarnings] = useState([]);
  const [sheets, setSheets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [openId, setOpenId] = useState(focusSheetId ?? null);
  useEffect(() => { if (focusSheetId) setOpenId(focusSheetId); }, [focusSheetId]);
  const [open, setOpen] = useState(null);
  // o botão de voltar mora fora do card, no CampaignDetail
  const showBack = Boolean(openId) && open?.buildStatus !== 'IN_BUILD';
  useEffect(() => {
    onSheetOpen?.(showBack ? () => () => setOpenId(null) : null);
    return () => onSheetOpen?.(null);
  }, [showBack, onSheetOpen]);
  const [creating, setCreating] = useState(false);
  const [free, setFree] = useState(false);
  const [newName, setNewName] = useState('');
  const [building, setBuilding] = useState(null);
  const [tab, setTab] = useState(fightSheetIds ? 'fight' : 'sheets');
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

  async function onImport(e) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    try {
      const res = await importSheet(campaignId, file);
      setWarnings(res.warnings ?? []);
      toast('Ficha importada. Aguarda aprovação do Mestre.');
      await reload();
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    }
  }

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
        <label className="label">
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
        focusSection={focusSection}
        onChange={(s) => { setOpen(s); reload(); }}
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
            <button type="button" className={tab === 'fight' ? 'btn-primary' : 'btn-secondary'} onClick={() => setTab('fight')}>Fechar luta</button>
          </>
        )}
        {!isMaster && (
          <button type="button" className="btn-primary" onClick={() => setCreating(true)}>Criar minha ficha</button>
        )}
        {!isMaster && (
          <label className="btn-secondary cursor-pointer">
            Importar ficha (.tatakai.json)
            <input type="file" accept=".json,application/json" className="sr-only" onChange={onImport} />
          </label>
        )}
      </div>
      {warnings.length > 0 && (
        <div className="panel p-4" role="status">
          <h2 className="panel-title mb-2">Avisos da importação</h2>
          <ul className="list-disc pl-5 text-sm">{warnings.map((w, i) => <li key={i}>{w}</li>)}</ul>
          <button type="button" className="btn-ghost mt-2" onClick={() => setWarnings([])}>Dispensar</button>
        </div>
      )}

      {tab === 'fight' && isMaster ? (
        <FightClosePanel campaignId={campaignId} sheets={sheets} preselected={fightSheetIds} onDone={() => { setTab('sheets'); reload(); }} />
      ) : tab === 'log' && isMaster ? (
        <ActivityLogPanel campaignId={campaignId} sheets={sheets} refreshKey={tick} />
      ) : sheets.length === 0 ? (
        <>
          <EmptyState title="Nenhuma ficha ainda" description="As fichas dos jogadores aparecerão aqui." />
          <GmSheets campaignId={campaignId} isMaster={isMaster} npcs={npcs} setNpcs={setNpcs} />
        </>
      ) : (
        <>
        <SheetCarousel sheets={sheets} userId={user?.userId} isMaster={isMaster} onOpen={setOpenId} />
        <GmSheets campaignId={campaignId} isMaster={isMaster} npcs={npcs} setNpcs={setNpcs} />
        </>
      )}
    </div>
  );
}
