import { useEffect, useState } from 'react';
import { getRollProfiles, listRollChannels, rollTest } from '../../api/rolls';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import RollReveal from '../rolls/RollReveal';
import RollBox from './RollBox';
import { ATTRS } from './sheetRules';

const BLANK = { advantage: 0, targetDowned: false, attackerDowned: false, localizedRegion: '', localizedPenalty: 0, editReason: '' };

/** Construtor do modo DETAILED: situacionais + edição de valores (motivo obrigatório). */
function Builder({ profile, busy, onRoll, onClose }) {
  const [f, setF] = useState({ ...BLANK, advantage: profile.advantage ?? 0 });
  const [edits, setEdits] = useState({});
  const set = (k, v) => setF((s) => ({ ...s, [k]: v }));
  const parts = [...(profile.hit ?? []), ...(profile.damage ?? [])].filter((p) => !p.dice);
  const overrides = Object.entries(edits).filter(([, v]) => v !== '').map(([source, v]) => ({ source, value: Number(v) }));
  const needReason = overrides.length > 0 && !f.editReason.trim();

  return (
    <div className="panel mt-2 flex flex-col gap-3 p-3">
      <div className="text-xs">
        <div><strong>Acerto:</strong> {profile.hitFormula}</div>
        {profile.damageFormula && <div><strong>Dano:</strong> {profile.damageFormula}</div>}
      </div>
      <ul className="flex flex-col gap-1 text-xs">
        {[...(profile.hit ?? []), ...(profile.damage ?? [])].map((p, i) => (
          <li key={i} className="flex items-center gap-2">
            <span className="flex-1">{p.part === 'DAMAGE' ? 'Dano' : 'Acerto'} · {p.source}: {p.dice ? `${p.dice.length ? '' : ''}dado` : p.value}</span>
            {!p.dice && (
              <input type="number" className="field w-20" aria-label={`Editar ${p.source}`} placeholder="valor"
                value={edits[p.source] ?? ''} onChange={(e) => setEdits((s) => ({ ...s, [p.source]: e.target.value }))} />
            )}
          </li>
        ))}
        {parts.length === 0 && null}
      </ul>
      <div className="grid gap-2 sm:grid-cols-3">
        <label className="text-xs uppercase">Vantagem (−3…+3)
          <input type="number" min={-3} max={3} className="field mt-1 w-full" value={f.advantage}
            onChange={(e) => set('advantage', Math.max(-3, Math.min(3, Number(e.target.value) || 0)))} />
        </label>
        <label className="text-xs uppercase">Região (localizado)
          <input className="field mt-1 w-full" maxLength={40} value={f.localizedRegion}
            onChange={(e) => set('localizedRegion', e.target.value)} />
        </label>
        <label className="text-xs uppercase">Penalidade (≤ 0)
          <input type="number" max={0} className="field mt-1 w-full" value={f.localizedPenalty}
            onChange={(e) => set('localizedPenalty', Math.min(0, Number(e.target.value) || 0))} />
        </label>
      </div>
      <div className="flex flex-wrap gap-4 text-sm">
        <label><input type="checkbox" checked={f.targetDowned} onChange={(e) => set('targetDowned', e.target.checked)} /> Alvo derrubado</label>
        <label><input type="checkbox" checked={f.attackerDowned} onChange={(e) => set('attackerDowned', e.target.checked)} /> Atacante derrubado</label>
      </div>
      {overrides.length > 0 && (
        <label className="text-xs uppercase">Motivo da edição (obrigatório)
          <input className="field mt-1 w-full" maxLength={200} value={f.editReason}
            onChange={(e) => set('editReason', e.target.value)} />
        </label>
      )}
      <div className="flex gap-2">
        <button type="button" className="btn-primary" disabled={busy || needReason}
          onClick={() => onRoll({
            mode: 'DETAILED', advantage: f.advantage, targetDowned: f.targetDowned, attackerDowned: f.attackerDowned,
            localizedRegion: f.localizedRegion.trim() || undefined,
            localizedPenalty: f.localizedRegion.trim() ? f.localizedPenalty : undefined,
            overrides: overrides.length ? overrides : undefined,
            editReason: overrides.length ? f.editReason.trim() : undefined,
          })}>
          {busy ? 'Rolando…' : 'Rolar detalhado'}
        </button>
        <button type="button" className="btn-ghost" onClick={onClose}>Fechar</button>
      </div>
    </div>
  );
}

const profileBody = (p) => ({
  sourceType: p.sourceType, sourceId: p.sourceId ?? undefined,
  variation: p.variation ?? undefined, basicAttack: p.basicAttack ?? undefined,
});

export default function RollsPanel({ sheetId, campaignId, isMaster, isNpc, refreshKey }) {
  const toast = useToast();
  const [profiles, setProfiles] = useState([]);
  const [channelSel, setChannelSel] = useState('PUBLIC');
  const [channels, setChannels] = useState([]);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [builder, setBuilder] = useState(null);

  useEffect(() => {
    let active = true;
    getRollProfiles(sheetId).then((d) => active && setProfiles(d)).catch((e) => toast(parseApiError(e).message, 'error'));
    return () => { active = false; };
  }, [sheetId, refreshKey, toast]);

  useEffect(() => {
    if (!campaignId) return;
    let active = true;
    listRollChannels(campaignId).then((d) => active && setChannels(d)).catch(() => {});
    return () => { active = false; };
  }, [campaignId]);

  const custom = channelSel.startsWith('CUSTOM:') ? channelSel.slice(7) : undefined;
  const channel = custom ? 'CUSTOM' : isMaster || channelSel !== 'PUBLIC' ? channelSel : undefined;

  async function run(body) {
    setBusy(true);
    try {
      const r = await rollTest(sheetId, {
        ...body, channel, customChannelId: custom, campaignId: isNpc ? campaignId : undefined,
      });
      setResult(r);
      setBuilder(null);
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }

  const key = (p) => `${p.sourceType}:${p.sourceId ?? p.basicAttack}:${p.variation ?? ''}`;

  return (
    <div className="flex flex-col gap-4">
      {(isMaster || channels.length > 0) && (
        <label className="flex items-center gap-3 text-sm">
          <span className="text-xs uppercase">Canal</span>
          <select className="field m-0 w-auto" value={channelSel} onChange={(e) => setChannelSel(e.target.value)}>
            <option value="PUBLIC">Público</option>
            {isMaster && <option value="GM_PRIVATE">Privado do Mestre</option>}
            {channels.map((c) => <option key={c.id} value={`CUSTOM:${c.id}`}>{c.name}</option>)}
          </select>
        </label>
      )}

      <div aria-live="polite">{result && <RollReveal key={result.id} roll={result} fresh />}</div>

      <div>
        <h4 className="caption mb-1">Ataques e técnicas</h4>
        {profiles.length === 0 && <p className="caption">Nenhum perfil disponível.</p>}
        <ul className="flex flex-col gap-2">
          {profiles.map((p) => (
            <li key={key(p)}>
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <strong className="flex-1">{p.name}{p.variation ? ` (${p.variation})` : ''}</strong>
                <span className="caption">{p.hitFormula}{p.damageFormula ? ` · ${p.damageFormula}` : ''}{p.spCost ? ` · ${p.spCost} SP` : ''}</span>
                <button type="button" className="btn-primary text-xs" disabled={busy}
                  onClick={() => run({ ...profileBody(p), mode: 'QUICK' })}>Rolar</button>
                <button type="button" className="btn-ghost text-xs" onClick={() => setBuilder(builder === key(p) ? null : key(p))}>Detalhar</button>
              </div>
              {builder === key(p) && (
                <Builder profile={p} busy={busy} onClose={() => setBuilder(null)}
                  onRoll={(extra) => run({ ...profileBody(p), ...extra })} />
              )}
            </li>
          ))}
        </ul>
      </div>

      <div>
        <h4 className="caption mb-1">Testes de atributo</h4>
        <div className="flex flex-wrap gap-2">
          {ATTRS.map((a) => (
            <button key={a.key} type="button" className="btn-ghost text-xs" disabled={busy}
              onClick={() => run({ sourceType: 'ATTRIBUTE_TEST', attribute: a.enumName, mode: 'QUICK' })}>{a.label}</button>
          ))}
        </div>
      </div>

      <div>
        <h4 className="caption mb-1">Expressão livre</h4>
        <RollBox sheetId={sheetId} channel={channel} customChannelId={custom} campaignId={isNpc ? campaignId : undefined} />
      </div>
    </div>
  );
}
