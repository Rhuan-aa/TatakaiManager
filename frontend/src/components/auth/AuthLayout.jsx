/** Layout de capa de mangá: painel "capa" (>=1024px) + formulário num .panel. */
export default function AuthLayout({ children }) {
  return (
    <div className="auth-page">
      <aside className="auth-cover halftone-bg slash-band" aria-hidden>
        <span className="caption justify-self-start">Manager</span>
        <h2 className="auth-logo">Tatakai</h2>
        <p className="caption justify-self-start">Cada luta conta uma história.</p>
      </aside>
      <main className="auth-form-side">
        <div className="w-full max-w-sm">
          <div className="auth-compact mb-6 text-center">
            <span className="title-stamp text-5xl inline-block">Tatakai</span>
            <p className="caption mt-2">Manager</p>
          </div>
          {children}
        </div>
      </main>
    </div>
  );
}
