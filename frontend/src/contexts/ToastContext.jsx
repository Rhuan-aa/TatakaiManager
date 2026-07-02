import { createContext, useContext, useCallback, useState } from 'react';
import { createPortal } from 'react-dom';

const ToastContext = createContext(null);

/** Hook de conveniência: `const toast = useToast(); toast('Salvo!'); toast('Falhou', 'error')`. */
export function useToast() {
  const ctx = useContext(ToastContext);
  return ctx?.push ?? (() => {});
}

const ICONS = {
  success: (
    <path d="M20 6 9 17l-5-5" />
  ),
  error: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 8v4M12 16h.01" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5M12 8h.01" />
    </>
  ),
};

const TONE = {
  success: 'text-green-400',
  error: 'text-red-400',
  info: 'text-zinc-300',
};

function ToastItem({ toast, onClose }) {
  return (
    <div
      role="status"
      onClick={onClose}
      className="pointer-events-auto flex cursor-pointer items-start gap-3 rounded-xl border border-zinc-800 bg-zinc-900/95 p-3.5 shadow-2xl shadow-black/50 ring-1 ring-white/5 backdrop-blur animate-[pop-in_160ms_ease-out]"
    >
      <svg
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className={`mt-0.5 shrink-0 ${TONE[toast.type] ?? TONE.info}`}
      >
        {ICONS[toast.type] ?? ICONS.info}
      </svg>
      <p className="min-w-0 flex-1 text-sm text-zinc-100">{toast.message}</p>
    </div>
  );
}

let counter = 0;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const remove = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (message, type = 'success') => {
      const id = ++counter;
      setToasts((prev) => [...prev, { id, message, type }]);
      setTimeout(() => remove(id), 3500);
      return id;
    },
    [remove]
  );

  return (
    <ToastContext.Provider value={{ push }}>
      {children}
      {createPortal(
        <div className="pointer-events-none fixed inset-x-0 bottom-4 z-[60] mx-auto flex w-full max-w-xs flex-col gap-2 px-4 sm:left-auto sm:right-4 sm:mx-0 sm:px-0">
          {toasts.map((t) => (
            <ToastItem key={t.id} toast={t} onClose={() => remove(t.id)} />
          ))}
        </div>,
        document.body
      )}
    </ToastContext.Provider>
  );
}
