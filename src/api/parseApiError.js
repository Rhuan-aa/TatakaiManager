export function parseApiError(error) {
  const data = error.response?.data;
  if (!data) return { message: 'Não foi possível conectar ao servidor.', fields: {} };
  return { message: data.message ?? 'Erro inesperado.', fields: data.fields ?? {} };
}
