import { useState } from 'react';

/** Seção com título que recolhe/expande o conteúdo. `title` pode ser nó; `aside` fica à direita do título. */
export default function Collapse({ title, aside, defaultOpen = true, className = '', titleClass = 'panel-title', children }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <section className={`flex flex-col gap-2 ${className}`}>
      <div className="flex flex-wrap items-center gap-2">
        <button type="button" className="flex flex-1 items-center gap-2 text-left" aria-expanded={open}
          onClick={() => setOpen((o) => !o)}>
          <span className={`flex-1 ${titleClass}`}>{title}</span>
          <span aria-hidden="true" className="text-xs">{open ? '▲' : '▼'}</span>
        </button>
        {aside}
      </div>
      {open && children}
    </section>
  );
}
