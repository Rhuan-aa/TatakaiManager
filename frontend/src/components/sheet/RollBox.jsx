import { useState } from 'react';
import { rollDice } from '../../api/characterSheets';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { DICE_REGEX } from './sheetRules';

const describe = (r) =>
  `[${r.diceResults.join(', ')}]${r.modifier ? ` ${r.modifier > 0 ? '+' : '−'} ${Math.abs(r.modifier)}` : ''}`;

export default function RollBox({ sheetId, onRolled }) {
  const toast = useToast();
  const [expression, setExpression] = useState('1d20');
  const [purpose, setPurpose] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [history, setHistory] = useState([]);

  const exprOk = DICE_REGEX.test(expression.trim());
  const canRoll = exprOk && purpose.trim() && !busy;

  async function handleRoll(e) {
    e.preventDefault();
    setBusy(true);
    try {
      const r = await rollDice(sheetId, { expression: expression.trim(), purpose: purpose.trim() });
      setResult(r);
      setHistory((h) => [r, ...h].slice(0, 10));
      onRolled?.(r);
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={handleRoll} className="flex flex-col gap-3">
      <div className="grid gap-2 sm:grid-cols-[8rem_1fr_auto] sm:items-end">
        <label className="text-xs uppercase">
          Expressão
          <input
            className="field mt-1 w-full"
            value={expression}
            onChange={(e) => setExpression(e.target.value)}
            aria-invalid={!exprOk}
            placeholder="2d6+3"
          />
        </label>
        <label className="text-xs uppercase">
          Propósito
          <input
            className="field mt-1 w-full"
            value={purpose}
            maxLength={200}
            onChange={(e) => setPurpose(e.target.value)}
            placeholder="Ex.: Ataque com a espada"
          />
        </label>
        <button type="submit" className="btn-primary" disabled={!canRoll}>
          {busy ? 'Rolando…' : 'Rolar'}
        </button>
      </div>
      {!exprOk && <p className="text-xs" style={{ color: 'var(--accent)' }}>Use o formato NdX±M (ex.: 1d20, 2d6+3).</p>}
      <div aria-live="polite" className="min-h-12">
        {result && (
          <div className="relative flex flex-wrap items-center gap-4">
            {(result.critical || result.fumble) && (
              <span key={result.id} className="onomatopoeia text-4xl">
                {result.critical ? 'CRÍTICO!' : 'FALHA!'}
              </span>
            )}
            <span className="panel-title text-5xl" aria-label={`Total ${result.total}`}>{result.total}</span>
            <div className="text-sm">
              <div className="font-bold">{result.expression}</div>
              <div>Dados: {describe(result)}</div>
              {result.purpose && <div className="opacity-80">{result.purpose}</div>}
            </div>
          </div>
        )}
      </div>
      {history.length > 1 && (
        <ol className="flex flex-col gap-1 text-sm" aria-label="Últimas rolagens">
          {history.slice(1).map((h) => (
            <li key={h.id} className="flex flex-wrap items-center gap-2">
              <span className="panel-title w-10 text-xl">{h.total}</span>
              <span>{h.expression} · {describe(h)}</span>
              {h.purpose && <span className="opacity-70">— {h.purpose}</span>}
              {h.critical && <span className="tag-accent tag">Crítico</span>}
              {h.fumble && <span className="tag">Falha</span>}
            </li>
          ))}
        </ol>
      )}
    </form>
  );
}
