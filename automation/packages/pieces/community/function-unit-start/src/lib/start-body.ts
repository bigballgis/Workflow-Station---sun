export function startRequestBody(functionUnitCode: unknown, fieldsJson: unknown): Record<string, unknown> {
  if (typeof functionUnitCode !== 'string' || functionUnitCode.trim() === '') {
    throw new Error('Function Unit Code is required');
  }
  const fields = parseFields(fieldsJson);
  return { functionUnitCode: functionUnitCode.trim(), fields };
}

function parseFields(fieldsJson: unknown): Record<string, unknown> {
  if (fieldsJson == null || String(fieldsJson).trim() === '') {
    return {};
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(String(fieldsJson));
  } catch {
    throw new Error('Fields JSON must be an object');
  }
  if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('Fields JSON must be an object');
  }
  if ('__subTables__' in parsed) {
    throw new Error('Start does not accept sub-table rows');
  }
  return parsed as Record<string, unknown>;
}
