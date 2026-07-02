import { createContext, useContext, useEffect, useState } from 'react';

const ThemeContext = createContext(null);

export function useTheme() {
  return useContext(ThemeContext) ?? { theme: 'dark', toggle: () => {} };
}

/**
 * Tema claro/escuro. Persiste em localStorage e aplica `data-theme` no <html>,
 * que dispara a inversão da escala zinc definida no index.css. O valor inicial
 * também é aplicado por um script inline no index.html (evita flash).
 */
export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(
    () => (typeof localStorage !== 'undefined' && localStorage.getItem('theme')) || 'dark'
  );

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('theme', theme);
  }, [theme]);

  const toggle = () => setTheme((t) => (t === 'dark' ? 'light' : 'dark'));

  return <ThemeContext.Provider value={{ theme, toggle }}>{children}</ThemeContext.Provider>;
}
