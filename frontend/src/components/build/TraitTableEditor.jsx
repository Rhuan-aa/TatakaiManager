import { useState } from 'react';
import { setTraitTable } from '../../api/characterBuild';
import { RARITY, rarityTagStyle } from '../ui/rarity';

const ORDER = ['COMMON', 'RARE', 'EPIC', 'LEGENDARY', 'DIVINE'];
const sizeOf = (r) => (r === 'DIVINE' ? 10 : 20);

/** Tabela de rolagem do jogador: escolhe os traços de cada raridade e a ordem (posição = resultado do dado). */
export default function TraitTableEditor({ build, pool, busy, act }) {
  const [rarity, setRarity] = useState('COMMON');
  const [drafts, setDrafts] = useState({});
  const size = sizeOf(rarity);
  const options = pool.filter((t) => t.rarity === rarity).sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));
  const saved = build.traitTables?.[rarity];
  const rows = drafts[rarity] ?? saved ?? Array(size).fill('');
  const set = (next) => setDrafts((d) => ({ ...d, [rarity]: next }));
  const complete = rows.length === size && rows.every(Boolean) && new Set(rows).size === size;

  const defaults = () => {
    const byNum = options.filter((t) => t.rollNumber != null).sort((a, b) => a.rollNumber - b.rollNumber);
    set(Array.from({ length: size }, (_, i) => byNum.find((t) => t.rollNumber === i + 1)?.id ?? ''));
  };
  const move = (i, d) => {
    const j = i + d;
    if (j < 0 || j >= size) return;
    const n = [...rows];
    [n[i], n[j]] = [n[j], n[i]];
    set(n);
  };
  const clear = () => {
    setDrafts((d) => { const n = { ...d }; delete n[rarity]; return n; });
    if (saved) act(() => setTraitTable(build.sheetId, { rarity, traitIds: [] }));
  };

  return (
    <details className="panel p-3">
      <summary className="panel-title cursor-pointer">Minha tabela de rolagem</summary>
      <div className="mt-3 flex flex-col gap-3">
        <p className="caption">Escolha {size} traços por raridade e a ordem: a posição é o resultado do dado. Sem tabela, vale a padrão do catálogo.</p>
        <div className="flex flex-wrap gap-2">
          {ORDER.map((r) => (
            <button key={r} type="button" className={rarity === r ? 'btn-primary' : 'btn-secondary'}
              style={rarityTagStyle(r)} onClick={() => setRarity(r)}>
              {RARITY[r].label}{build.traitTables?.[r] ? ' ✓' : ''}
            </button>
          ))}
        </div>
        {options.length < size ? (
          <p className="caption">O pool tem só {options.length} traços desta raridade; a tabela padrão será usada.</p>
        ) : (
          <>
            <ol className="flex flex-col gap-1">
              {rows.map((id, i) => (
                <li key={i} className="flex items-center gap-2">
                  <span className="tag w-8 justify-center">{i + 1}</span>
                  <select className="field flex-1" value={id} aria-label={`Resultado ${i + 1}`}
                    onChange={(e) => { const n = [...rows]; n[i] = e.target.value; set(n); }}>
                    <option value="">Escolher…</option>
                    {options.map((t) => (
                      <option key={t.id} value={t.id} disabled={rows.includes(t.id) && t.id !== id}>{t.name}</option>
                    ))}
                  </select>
                  <button type="button" className="btn-ghost" onClick={() => move(i, -1)} aria-label="Subir">↑</button>
                  <button type="button" className="btn-ghost" onClick={() => move(i, 1)} aria-label="Descer">↓</button>
                </li>
              ))}
            </ol>
            <div className="flex flex-wrap gap-2">
              <button type="button" className="btn-secondary" onClick={defaults}>Preencher pela tabela padrão</button>
              <button type="button" className="btn-primary" disabled={busy || !complete}
                onClick={() => act(() => setTraitTable(build.sheetId, { rarity, traitIds: rows }))}>Salvar tabela</button>
              <button type="button" className="btn-ghost" onClick={clear}>Limpar</button>
            </div>
          </>
        )}
      </div>
    </details>
  );
}
