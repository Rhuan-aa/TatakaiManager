import { useEffect, useState } from 'react';
import { getRollProfiles } from '../../api/rolls';
import { useWeapons } from '../ui/useCatalog';
import { BASIC_HINT, LOCALIZED_REGIONS, GRIP_LABEL, SIDED_BASICS, STRIKE_SIDE_LABEL } from './combatLabels';

const limit = (n) => (n === Infinity ? '∞' : n);

const DAMAGE_TYPE = { BLUNT: 'contundente', SLASH: 'cortante', PIERCE: 'perfurante', FIRE: 'flamejante' };

/** Resumo das propriedades da arma (capítulo Armas). */
function weaponHint(w) {
  const p = [DAMAGE_TYPE[w.damageType]];
  if (w.rangeM) p.push(`alcance ${w.rangeM} m`);
  if (w.light) p.push('Leve');
  if (w.heavyStr) p.push(`Pesada (Força ${w.heavyStr})`);
  if (w.large) p.push('Grande');
  if (w.defensive) p.push('Defensiva');
  if (w.impact) p.push(`Impactante (+${w.impact})`);
  if (w.flexible) p.push('Flexível');
  if (w.fragile) p.push('Quebradiça');
  if (w.entangle) p.push('Enredar');
  if (w.burst) p.push(`Rajada (${w.burst})`);
  if (w.dispersion) p.push('Dispersão');
  if (w.unstable) p.push(`Instável (${w.unstable})`);
  if (w.explosiveRadius) p.push(`área ${w.explosiveRadius} m (ND 14)`);
  if (w.firearm || w.group === 'FIREARM') p.push('reação do alvo com desvantagem');
  return p.filter(Boolean).join(' · ');
}

const KINDS = [{ k: 'MAJOR', label: 'Ação maior', pa: 3 }, { k: 'MINOR_OFFENSIVE', label: 'Menor ofensiva', pa: 2 }];

export default function AttackBuilder({ participant: p, participants, initialKind = 'MAJOR', onSubmit, onCancel, cancelLabel = 'Fechar' }) {
  const [profiles, setProfiles] = useState(null);
  const [idx, setIdx] = useState(0);
  const [kind, setKind] = useState(initialKind);
  const [targets, setTargets] = useState([]);
  const [splashTargets, setSplashTargets] = useState([]);
  const [region, setRegion] = useState('');
  const [penalty, setPenalty] = useState(0);
  const [custom, setCustom] = useState(false); // região improvisada
  const [details, setDetails] = useState('');
  const [side, setSide] = useState('');
  const [grip, setGrip] = useState('ARM');
  const [squeeze, setSqueeze] = useState(false);
  const [run, setRun] = useState(0);
  const [surface, setSurface] = useState(false);
  const [distance, setDistance] = useState('');
  const weapons = useWeapons();

  useEffect(() => {
    let active = true;
    getRollProfiles(p.sheetId).then((r) => active && setProfiles(r)).catch(() => active && setProfiles([]));
    return () => { active = false; };
  }, [p.sheetId]);

  const prof = profiles?.[idx];
  const grab = prof?.basicAttack === 'AGARRAO';
  const mods = prof?.modules ?? [];
  const wallable = mods.some((m) => m === 'Esmagar' || m.startsWith('Empurrar')) || prof?.basicAttack === 'EMPURRAO';
  // §41: Varredura atinge 2 alvos, Círculo todos; o resto, 1
  const weapon = prof?.weaponKey ? weapons[prof.weaponKey] : null;

  useEffect(() => {
    if (weapon?.explosiveRadius == null) {
      setSplashTargets([]);
    }
  }, [weapon?.explosiveRadius]);

  const maxTargets = weapon?.explosiveRadius || mods.includes('Círculo') ? Infinity
    : mods.includes('Supressão') ? 3
    : mods.includes('Varredura') || mods.includes('Transpassar') ? 2 : 1;
  const ranged = weapon && (weapon.rangeM || weapon.throwM || prof?.variation === 'THROWN' || weapon.large || weapon.dispersion || mods.includes('Arremessar'));
  const oneHandWeapon = weapon && !weapon.twoHands && !weapon.heavyStr && !weapon.large && !(weapon.rangeM && !weapon.compact);
  const sided = prof?.basicAttack ? SIDED_BASICS.includes(prof.basicAttack) || oneHandWeapon : !!prof && (!weapon || oneHandWeapon);
  const stealth = (t) => t.conditions.some((c) => c.key === 'FURTIVO');
  const toggle = (id) => {
    setSplashTargets((cur) => cur.filter((x) => x !== id));
    setTargets((cur) => (cur.includes(id) ? cur.filter((x) => x !== id)
      : maxTargets === 1 ? [id] : cur.length >= maxTargets ? cur : [...cur, id]));
  };
  const others = participants.filter((t) => t.id !== p.id);
  const enemies = others.filter((t) => t.team !== p.team);
  const allies = others.filter((t) => t.team === p.team);
  const targetRow = (t) => {
    const hidden = stealth(t);
    const r = t.resources;
    const conds = t.conditions.map((c) => c.name).join(', ');
    return (
      <label key={t.id} className="flex items-start gap-1.5 border-2 border-[var(--line)] px-2 py-1 text-xs"
        title={hidden ? 'Furtivo: não pode ser alvo direto' : undefined}>
        <input type="checkbox" className="mt-0.5" disabled={hidden || (!targets.includes(t.id) && maxTargets > 1 && targets.length >= maxTargets)}
          checked={targets.includes(t.id)} onChange={() => toggle(t.id)} />
        <span className="min-w-0">
          <strong>{t.name}</strong>{hidden ? ' (Furtivo)' : ''}
          {r && <span className="ml-1 opacity-80">HP {r.currentHp}/{r.hpMax}</span>}
          {conds && <span className="block truncate opacity-75">{conds}</span>}
        </span>
      </label>
    );
  };
  const toggleSplash = (id) => setSplashTargets((cur) => (cur.includes(id)
    ? cur.filter((x) => x !== id)
    : [...cur, id]));

  const ammoStock = weapon?.ammo && p.gear
    ? p.gear
      .filter((i) => i.consumableKey === weapon.ammo && i.location !== 'DROPPED')
      .reduce((sum, i) => sum + (i.quantity ?? 0), 0)
    : null;

  function submit(e) {
    e.preventDefault();
    const validSplash = weapon?.explosiveRadius != null
      ? splashTargets.filter((id) => !targets.includes(id) && id !== p.id)
      : [];
    onSubmit({
      attackerId: p.id,
      sourceType: prof.basicAttack ? 'BASIC_ATTACK' : 'TECHNIQUE',
      ...(prof.sourceId ? { sourceId: prof.sourceId } : {}),
      ...(prof.variation ? { variation: prof.variation } : {}),
      ...(prof.basicAttack ? { basicAttack: prof.basicAttack } : {}),
      actionKind: kind,
      targetIds: targets,
      ...(validSplash.length > 0 ? { splashTargetIds: validSplash } : {}),
      ...(region.trim() ? { region: region.trim() } : {}),
      regionPenalty: region.trim() ? -Math.abs(penalty) : 0,
      ...(details.trim() ? { details: details.trim() } : {}),
      ...(sided && side ? { strikeSide: side } : {}),
      ...(grab ? { grip, squeeze } : {}),
      ...(mods.includes('Ímpeto') && run > 0 ? { runMeters: run } : {}),
      ...(wallable ? { surface } : {}),
      ...(ranged && distance !== '' ? { distanceM: Number(distance) } : {}),
    });
  }

  return (
    <form className="panel space-y-3 p-3" onSubmit={submit}>
      <span className="panel-title text-base">Atacar com {p.name}</span>
      {!profiles && <p className="caption">Carregando…</p>}
      {profiles?.length === 0 && <p className="caption">Nenhum perfil de ataque disponível.</p>}
      {profiles?.length > 0 && (
        <>
          <div className="flex flex-wrap gap-3">
            <label className="text-xs">Ataque
              <select className="field" value={idx} onChange={(e) => setIdx(Number(e.target.value))}>
                {profiles.map((x, i) => (
                  <option key={i} value={i}>{x.name}{x.variation && x.basicAttack !== 'ARMA' ? ` (${x.variation})` : ''} · {x.spCost} SP</option>
                ))}
              </select>
            </label>
            <label className="text-xs">Tipo de ação
              <select className="field" value={kind} onChange={(e) => setKind(e.target.value)}>
                {KINDS.map((x) => <option key={x.k} value={x.k}>{x.label} · {x.pa} PA</option>)}
              </select>
            </label>
            {sided && (
              <label className="text-xs">Lado do golpe
                <select className="field" value={side} onChange={(e) => setSide(e.target.value)}>
                  <option value="">{prof?.basicAttack ? 'Frente (padrão)' : '—'}</option>
                  {Object.entries(STRIKE_SIDE_LABEL).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                </select>
              </label>
            )}
            {grab && (
              <>
                <label className="text-xs">Ponto do agarre
                  <select className="field" value={grip} onChange={(e) => setGrip(e.target.value)}>
                    {Object.entries(GRIP_LABEL).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                  </select>
                </label>
                <label className="flex items-center gap-1 self-end text-xs" title="Sem apertar/torcer o Agarrão não causa dano">
                  <input type="checkbox" checked={squeeze} onChange={(e) => setSqueeze(e.target.checked)} /> Apertar/torcer (1d2 + FOR)
                </label>
              </>
            )}
            {ranged && (
              <label className="text-xs" title="Disparo/arremesso: além do alcance com desvantagem, até o dobro; colado (≤ 1,5 m) com desvantagem">
                Distância (m)
                <input type="number" min={0} max={5000} step={0.5} className="field !w-24" value={distance}
                  onChange={(e) => setDistance(e.target.value)} placeholder={weapon?.rangeM ? `${weapon.rangeM} m` : ''} />
              </label>
            )}
            {mods.includes('Ímpeto') && (
              <label className="text-xs" title="+2 de dano a cada 1,5 m em linha reta (máx. +6)">Corrida (m)
                <input type="number" min={0} max={100} step={1.5} className="field !w-20" value={run}
                  onChange={(e) => setRun(Math.max(0, Number(e.target.value)))} />
              </label>
            )}
            {wallable && (
              <label className="flex items-center gap-1 self-end text-xs" title="Empurrão/Empurrar: o alvo bate na parede (Impacto 1d4+3). Esmagar: prensa o alvo (1d4+3 e +1 PQ)">
                <input type="checkbox" checked={surface} onChange={(e) => setSurface(e.target.checked)} /> Parede/obstáculo junto do alvo
              </label>
            )}
            <label className="text-xs">Ataque localizado
              <select className="field" value={custom ? '__custom' : region}
                onChange={(e) => {
                  const v = e.target.value;
                  setCustom(v === '__custom');
                  setRegion(v === '__custom' ? '' : v);
                  if (!v) setPenalty(0);
                }}>
                <option value="">Nenhum</option>
                {LOCALIZED_REGIONS.map((r) => <option key={r} value={r}>{r}</option>)}
                <option value="__custom">Improvisado…</option>
              </select>
            </label>
            {custom && (
              <label className="text-xs">Local
                <input className="field" maxLength={40} value={region} onChange={(e) => setRegion(e.target.value)} placeholder="Costelas…" />
              </label>
            )}
            {(custom || region) && (
              <label className="text-xs" title="Valor informado pelo Mestre; digite antes de rolar">Redução (Mestre)
                <input type="number" max={0} className="field !w-20" value={penalty}
                  onChange={(e) => setPenalty(Math.min(0, Number(e.target.value)))} />
              </label>
            )}
            <label className="w-full text-xs">Detalhes do ataque (vai para o histórico)
              <textarea className="field w-full" rows={2} maxLength={300} value={details}
                onChange={(e) => setDetails(e.target.value)} placeholder="Como foi o golpe…" />
            </label>
          </div>
          {prof && <p className="caption">Acerto: {prof.hitFormula} · Dano: {prof.damageFormula ?? 'nenhum'}</p>}
          {BASIC_HINT[prof?.basicAttack] && <p className="caption">{BASIC_HINT[prof.basicAttack]}</p>}
          {weapon && (
            <p className="caption">
              {weaponHint(weapon)}{ammoStock != null ? ` · Munição: ${ammoStock}` : ''}
            </p>
          )}
          <fieldset className="space-y-2">
            <legend className="caption">
              Alvos {targets.length}/{limit(maxTargets)}{maxTargets === 2 ? ` (${mods.includes('Transpassar') ? 'Transpassar' : 'Varredura'})` : maxTargets === 3 ? ' (Supressão)' : maxTargets === Infinity ? ' (Círculo)' : ''}
            </legend>
            <div className="grid gap-1 sm:grid-cols-2">
              {enemies.map(targetRow)}
              {enemies.length === 0 && <p className="caption">Nenhum inimigo.</p>}
            </div>
            {allies.length > 0 && (
              <details>
                <summary className="caption cursor-pointer">Aliados ({allies.length})</summary>
                <div className="mt-1 grid gap-1 sm:grid-cols-2">{allies.map(targetRow)}</div>
              </details>
            )}
          </fieldset>
          {weapon?.explosiveRadius != null && (
            <fieldset className="flex flex-wrap gap-3">
              <legend className="caption">
                No raio (metade do dano)
              </legend>
              <p className="-mt-1 w-full text-xs opacity-75">Não reagem; recebem metade do dano.</p>
              {participants.filter((t) => t.id !== p.id && !targets.includes(t.id)).map((t) => (
                <label key={t.id} className="flex items-center gap-1 text-xs">
                  <input type="checkbox" checked={splashTargets.includes(t.id)} onChange={() => toggleSplash(t.id)} />
                  {t.name}
                </label>
              ))}
              {participants.filter((t) => t.id !== p.id && !targets.includes(t.id)).length === 0 && (
                <span className="text-xs opacity-75">Nenhum outro participante no raio.</span>
              )}
            </fieldset>
          )}
        </>
      )}
      <div className="flex gap-2">
        <button className="btn-primary" type="submit" disabled={!prof || targets.length === 0}>Declarar ataque</button>
        <button className="btn-ghost" type="button" onClick={onCancel}>{cancelLabel}</button>
      </div>
    </form>
  );
}
