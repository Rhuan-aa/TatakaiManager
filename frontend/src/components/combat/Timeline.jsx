export default function Timeline({ events }) {
  if (!events.length) return <p className="caption">Nenhum evento ainda.</p>;
  return (
    <ul className="max-h-[60vh] space-y-1 overflow-y-auto text-sm">
      {[...events].reverse().map((e) => (
        <li key={e.id} className="border-l-4 border-[var(--line)] pl-2">
          <span className="tag mr-2">R{e.round}</span>{e.text}
        </li>
      ))}
    </ul>
  );
}
