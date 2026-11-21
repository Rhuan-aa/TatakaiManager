import { useEffect, useState } from 'react';
import { listRules, updateRule } from '../../api/campaignRules';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';

const PARAM_SELECTS = {
  TRAIT_METHOD: {
    key: 'method',
    label: 'Método de traços',
    options: [
      ['CLASSIC', 'Clássico'],
      ['BALANCED_CHOICE', 'Escolha Balanceada'],
      ['PRODIGY', 'Prodígio'],
    ],
  },
  DIVINE_INTERVENTION: {
    key: 'mode',
    label: 'Modo',
    options: [
      ['ROLL_100_GRANTS_DIVINE', '100 concede Divino'],
      ['FREE_DIVINE_TRAIT', 'Traço Divino gratuito'],
    ],
  },
};

const RULES = {
  MAX_RANDOM_ATTRIBUTES: ['Atributos aleatórios máximos', '7d20 em ordem, sem pontos livres'],
  TRAIT_METHOD: ['Método de traços', 'Como os traços são obtidos na Construção Inicial'],
  DIVINE_INTERVENTION: ['Intervenção Divina', 'Traços Divinos na Construção Inicial'],
  MAX_RANDOM_CLAN: ['Clã aleatório máximo', 'Clã sorteado no d20'],
  HEIGHT_CATEGORIES: ['Categorias de altura', 'Diferença de altura em combate'],
  KNOCKOUT_SEQUEL: ['Sequela de Nocaute', 'Nocaute pode deixar sequelas'],
  BURNOUT: ['Burnout', 'Burnout após 4 rodadas no Ápice'],
  CRITICAL_CLASH: ['Choque de Críticos', 'Críticos simultâneos se chocam'],
  EVENTUAL_TRAITS: ['Traços eventuais', 'Matriz de Desejos (veto 3 / foco 2)'],
  EXTRA_SPEC_SLOTS: ['Slots extras de Spec', 'Slots extras de Spec por mérito, com tetos por raridade'],
  GM_REROLLS: ['Rerrolagem pelo Mestre', 'Mestre pode mandar rerrolar na Construção Inicial'],
  FREE_CREATION: ['Criação livre', 'Jogadores criam ficha livremente, sem Construção Inicial'],
  AMMO_STOCK: ['Estoque de munição', 'Recarregar gasta munição do inventário'],
};

// Regras que só mexem na Construção Inicial
const BUILD = ['MAX_RANDOM_ATTRIBUTES', 'TRAIT_METHOD', 'DIVINE_INTERVENTION', 'MAX_RANDOM_CLAN', 'EVENTUAL_TRAITS',
  'EXTRA_SPEC_SLOTS', 'GM_REROLLS', 'FREE_CREATION'];

export default function RuleSettings({ campaignId, isMaster }) {
  const toast = useToast();
  const [rules, setRules] = useState(null);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState('');

  useEffect(() => {
    let active = true;
    listRules(campaignId)
      .then((data) => active && setRules(data))
      .catch((err) => active && setError(parseApiError(err).message));
    return () => {
      active = false;
    };
  }, [campaignId]);

  async function save(rule, enabled, params) {
    setSaving(rule.ruleKey);
    try {
      const updated = await updateRule(campaignId, rule.ruleKey, { enabled, params });
      setRules((prev) => prev.map((r) => (r.ruleKey === updated.ruleKey ? updated : r)));
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setSaving('');
    }
  }

  if (error) return <p className="caption">{error}</p>;
  if (!rules) return <div className="skeleton h-24 w-full" />;

  const groups = [
    ['Construção Inicial', 'Mudanças valem para construções iniciadas depois.', BUILD],
    ['Combate e itens', null, null],
  ];
  const known = rules.filter((r) => RULES[r.ruleKey]);

  return (
    <div className="flex flex-col gap-5">
      {groups.map(([title, note, keys]) => {
        const list = known.filter((r) => (keys ? keys.includes(r.ruleKey) : !BUILD.includes(r.ruleKey)));
        return list.length > 0 && (
          <div key={title} className="flex flex-col gap-2">
            <div className="flex flex-wrap items-baseline gap-x-3">
              <h4 className="label">{title}</h4>
              {note && <span className="hint">{note}</span>}
            </div>
            <ul className="grid grid-cols-1 items-start gap-2 md:grid-cols-2">
              {list.map((rule) => {
                const [name, desc] = RULES[rule.ruleKey];
                const sel = PARAM_SELECTS[rule.ruleKey];
                const busy = saving === rule.ruleKey;
                const id = `rule-${rule.ruleKey}`;
                return (
                  <li key={rule.ruleKey} className={`rule-row${rule.enabled ? ' is-on' : ''}`}>
                    <label htmlFor={id} className="rule-main">
                      <input id={id} type="checkbox" role="switch" className="rule-switch" checked={rule.enabled}
                        disabled={!isMaster || busy} onChange={(e) => save(rule, e.target.checked, rule.params)} />
                      <span className="min-w-0">
                        <span className="rule-name">{name}</span>
                        <span className="rule-desc">{desc}</span>
                      </span>
                    </label>
                    {sel && (
                      <select aria-label={`${sel.label} (${name})`} className="field" value={rule.params?.[sel.key] ?? sel.options[0][0]}
                        disabled={!isMaster || busy} onChange={(e) => save(rule, rule.enabled, { ...rule.params, [sel.key]: e.target.value })}>
                        {sel.options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                      </select>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        );
      })}
    </div>
  );
}
