// Texto do catálogo com o mínimo de markdown que o conversor preserva:
// parágrafos, listas com "- " (aninhadas por indentação) e tabelas com "|".
// ponytail: parser de linha simples, sem negrito/links; trocar por lib se o texto ficar mais rico.

const TABLE_SEPARATOR = /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$/;

function cells(line) {
  return line.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim());
}

function parseBlocks(text) {
  const lines = text.replace(/\r/g, '').split('\n');
  const blocks = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (!line.trim()) { i += 1; continue; }

    if (line.trim().startsWith('|')) {
      const rows = [];
      while (i < lines.length && lines[i].trim().startsWith('|')) {
        if (!TABLE_SEPARATOR.test(lines[i])) rows.push(cells(lines[i]));
        i += 1;
      }
      blocks.push({ type: 'table', rows });
      continue;
    }

    if (/^\s*[-•]\s+/.test(line)) {
      const items = [];
      while (i < lines.length && /^\s*[-•]\s+/.test(lines[i])) {
        const depth = Math.floor((lines[i].match(/^\s*/)[0].length) / 2);
        items.push({ depth, text: lines[i].replace(/^\s*[-•]\s+/, '') });
        i += 1;
      }
      blocks.push({ type: 'list', items });
      continue;
    }

    const para = [];
    while (i < lines.length && lines[i].trim() && !lines[i].trim().startsWith('|') && !/^\s*[-•]\s+/.test(lines[i])) {
      para.push(lines[i].trim());
      i += 1;
    }
    blocks.push({ type: 'p', text: para.join(' ') });
  }
  return blocks;
}

export default function RichText({ text, className = '' }) {
  if (!text) return null;
  return (
    <div className={`space-y-2 leading-relaxed ${className}`}>
      {parseBlocks(text).map((b, idx) => {
        if (b.type === 'table') {
          const [head, ...body] = b.rows;
          return (
            <div key={idx} className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr>
                    {head.map((c, j) => (
                      <th key={j} className="border-2 border-[var(--line)] bg-[var(--bg)] px-2 py-1 text-left font-semibold">{c}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {body.map((row, r) => (
                    <tr key={r}>
                      {row.map((c, j) => (
                        <td key={j} className="border-2 border-[var(--line)] px-2 py-1 align-top">{c}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          );
        }
        if (b.type === 'list') {
          return (
            <ul key={idx} className="space-y-1">
              {b.items.map((it, j) => (
                <li key={j} className="flex gap-2" style={{ paddingLeft: `${it.depth * 1.25}rem` }}>
                  <span aria-hidden className="mt-2 h-1.5 w-1.5 shrink-0 bg-[var(--accent)]" />
                  <span>{it.text}</span>
                </li>
              ))}
            </ul>
          );
        }
        return <p key={idx}>{b.text}</p>;
      })}
    </div>
  );
}
