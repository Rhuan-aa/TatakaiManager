const svg = (children) => (
  <svg
    width="100%"
    height="100%"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    className="block"
  >
    {children}
  </svg>
);

const ICONS = {
  grid: svg(
    <>
      <rect x="3" y="3" width="7" height="7" rx="0" />
      <rect x="14" y="3" width="7" height="7" rx="0" />
      <rect x="3" y="14" width="7" height="7" rx="0" />
      <rect x="14" y="14" width="7" height="7" rx="0" />
    </>
  ),
  sheet: svg(
    <>
      <path d="M6 3h9l4 4v14H6Z" />
      <path d="M14 3v5h5M9 12h7M9 16h7" />
      <circle cx="10" cy="9" r="1" fill="currentColor" />
    </>
  ),
  lock: svg(<path d="M6 11h12v9H6ZM8.500 11V8a3.500 3.500 0 0 1 7 0v3" />),
  plus: svg(<path d="M12 5v14M5 12h14" />),
  back: svg(<path d="M19 12H5M12 19l-7-7 7-7" />),
  users: svg(
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c0-4 3.5-6 8-6s8 2 8 6" />
    </>
  ),
  calendar: svg(
    <>
      <rect x="3" y="4" width="18" height="17" rx="2" />
      <path d="M3 9h18M8 2v4M16 2v4" />
    </>
  ),
  swords: svg(
    <>
      <path d="M4 4l9 9M4 4v4l1 1M4 4h4l1 1" />
      <path d="M20 4l-9 9M20 4v4l-1 1M20 4h-4l-1 1" />
      <path d="M9 15l-4 4M15 15l4 4M7 17l-2-2M17 17l2-2" />
    </>
  ),
  scroll: svg(
    <>
      <path d="M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z" />
      <path d="M9 8h6M9 12h6M9 16h4" />
    </>
  ),
  book: svg(
    <>
      <path d="M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2V5Z" />
      <path d="M4 19a2 2 0 0 1 2-2h13M9 7h6" />
    </>
  ),
  settings: svg(
    <>
      <path d="M4 6h16M4 12h16M4 18h16" />
      <circle cx="9" cy="6" r="2" fill="currentColor" />
      <circle cx="16" cy="12" r="2" fill="currentColor" />
      <circle cx="11" cy="18" r="2" fill="currentColor" />
    </>
  ),
  logout: svg(
    <>
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <path d="M16 17l5-5-5-5M21 12H9" />
    </>
  ),
  sun: svg(
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </>
  ),
  moon: svg(<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />),
  dice: svg(
    <>
      <rect x="3" y="3" width="18" height="18" rx="0" />
      <circle cx="8" cy="8" r="1.3" fill="currentColor" />
      <circle cx="16" cy="8" r="1.3" fill="currentColor" />
      <circle cx="12" cy="12" r="1.3" fill="currentColor" />
      <circle cx="8" cy="16" r="1.3" fill="currentColor" />
      <circle cx="16" cy="16" r="1.3" fill="currentColor" />
    </>
  ),
  check: svg(<path d="M4 12.5l5 5L20 6.5" />),
  flame: svg(<path d="M12 3c1 4 5 5 5 10a5 5 0 0 1-10 0c0-3 2-4 2-7 1 1 2 2 3 4 1-2 0-5 0-7Z" />),
  snow: svg(<path d="M12 2v20M3.3 7l17.4 10M3.3 17l17.4-10M9 4l3 2 3-2M9 20l3-2 3 2" />),
  drop: svg(<path d="M12 3s6 7 6 11a6 6 0 0 1-12 0c0-4 6-11 6-11Z" />),
  bolt: svg(<path d="M13 2 4 14h7l-1 8 9-12h-7Z" />),
  spiral: svg(<path d="M12 12a2 2 0 1 1 2-2 4 4 0 1 1-4-4 6 6 0 1 1-6 6" />),
  wind: svg(<path d="M3 8h11a3 3 0 1 0-3-3M3 12h16a3 3 0 1 1-3 3M3 16h8" />),
  hand: svg(<path d="M7 13V6a1.5 1.5 0 0 1 3 0v5M10 10V4a1.5 1.5 0 0 1 3 0v6M13 10V5a1.5 1.5 0 0 1 3 0v6M16 11V8a1.5 1.5 0 0 1 3 0v6a7 7 0 0 1-7 7h-1a6 6 0 0 1-5-3l-2.5-4.5a1.5 1.5 0 0 1 2.5-1.5L7 15" />),
  circle: svg(<circle cx="12" cy="12" r="7" />),
  forward: svg(<path d="M5 12h14M12 5l7 7-7 7" />),
  wrench: svg(<path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-3 3-3-1-1-3Z" />),
  rest: svg(
    <>
      <circle cx="12" cy="5" r="2" />
      <path d="M12 8v5M4 20l8-7 8 7M7 20h10" />
    </>
  ),
  heart: svg(<path d="M12 20S4 14.5 4 9a4 4 0 0 1 8-1 4 4 0 0 1 8 1c0 5.500-8 11-8 11Z" />),
  bandage: svg(<path d="M9 3h6v6h6v6h-6v6H9v-6H3V9h6Z" />),
  pack: svg(
    <>
      <path d="M6 8a6 6 0 0 1 12 0v13H6Z" />
      <path d="M9 14h6v4H9ZM9 5h6" />
    </>
  ),
  scale: svg(
    <>
      <path d="M12 3v18M5 21h14M4 7h16" />
      <path d="M4 7l-2 7h6ZM20 7l-2 7h6Z" />
    </>
  ),
  star: svg(<path d="M12 3l2.7 5.800 6.300.7-4.700 4.300 1.300 6.200L12 17l-5.600 3 1.300-6.200L3 9.500l6.300-.7Z" />),
  build: svg(
    <>
      <path d="M3 21l9-9M14 4l6 6-3 3-6-6Z" />
    </>
  ),
  pencil: svg(<path d="M4 20h4L19 9l-4-4L4 16Zm9-13 4 4" />),
  trash: svg(<path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6" />),
  search: svg(<><circle cx="11" cy="11" r="6" /><path d="m20 20-4.500-4.500" /></>),
  clock: svg(<><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>),
};

export const ICON_NAMES = Object.keys(ICONS);

/** Ícone de traço (estilo brutalista do site): herda a cor do texto. `size` em px. */
export default function Icon({ name, size = 20, className = '' }) {
  const node = ICONS[name] ?? ICONS.grid;
  return <span className={`inline-flex shrink-0 ${className}`} style={{ width: size, height: size }} aria-hidden="true">{node}</span>;
}
