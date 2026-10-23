import { useState } from 'react';
import { submitBuild } from '../../api/characterBuild';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { ATTRS } from '../sheet/sheetRules';
import { RARITY, rarityTagStyle } from '../ui/rarity';
import { StepActions } from './buildUi';

export default function ReviewStep({ build, canAct, onBack, onDone }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [burst, setBurst] = useState(null);
  const layers = build.layers ?? {};
  const r = build.resources;

  async function submit() {
    setBusy(true);
    try {
      const sheet = await submitBuild(build.sheetId);
      setBurst({ text: 'ENVIADO!', k: Date.now() });
      toast('Ficha enviada para aprovação.');
      onDone?.(sheet);
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="overflow-x-auto">
        <table className="w-full text-center text-sm">
          <caption className="sr-only">Camadas de atributos</caption>
          <thead>
            <tr className="panel-title">
              {['Atributo', 'Base', 'Clã', 'Traços', '%', 'Após %', 'Livres', 'Final'].map((h) => <th key={h} scope="col" className="p-1">{h}</th>)}
            </tr>
          </thead>
          <tbody>
            {ATTRS.map((a) => {
              const l = layers[a.enumName];
              if (!l) return null;
              return (
                <tr key={a.key}>
                  <th scope="row" className="p-1">{a.label}</th>
                  <td>{l.base}</td><td>{l.clanFlat}</td><td>{l.traitFlat}</td><td>{l.percent}%</td>
                  <td>{l.afterPercent}</td><td>{l.freePoints}</td><td className="font-bold">{l.finalValue}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {r && (
        <ul className="flex flex-wrap gap-3" aria-label="Recursos">
          <li className="panel p-3"><span className="text-xs">PV</span> <span className="panel-title text-2xl">{r.hpMax}</span></li>
          <li className="panel p-3"><span className="text-xs">SP</span> <span className="panel-title text-2xl">{r.spMax}</span></li>
          <li className="panel p-3"><span className="text-xs">PQ</span> <span className="panel-title text-2xl">{r.kpMax}</span></li>
        </ul>
      )}
      <div>
        <h3 className="panel-title">Traços</h3>
        <ul className="flex flex-wrap gap-2">
          {(build.traitSlots ?? []).map((s) => (
            <li key={s.index} className="tag" style={rarityTagStyle(s.rarity)}>{s.traitName ?? 'Pendente'}{s.rarity ? ` · ${RARITY[s.rarity]?.label}` : ''}</li>
          ))}
          {(build.traitSlots ?? []).length === 0 && <li className="opacity-70">Nenhum.</li>}
        </ul>
        <h3 className="panel-title mt-2">Clãs</h3>
        <ul className="flex flex-wrap gap-2">
          {(build.clans ?? []).map((c) => <li key={c.id} className="tag-accent tag">{c.name}</li>)}
          {(build.clans ?? []).length === 0 && <li className="opacity-70">Nenhum.</li>}
        </ul>
      </div>
      {burst && <span key={burst.k} className="onomatopoeia text-3xl">{burst.text}</span>}
      <StepActions canAct={canAct} busy={busy} onBack={onBack} onConfirm={submit} confirmLabel="Enviar para aprovação" />
    </div>
  );
}
