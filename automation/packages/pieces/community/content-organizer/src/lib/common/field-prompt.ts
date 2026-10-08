export type FieldType = 'text' | 'date' | 'number';

export type FieldSpec = {
  /** Key in the result; the flow writes it back as the process variable of the same name. */
  key: string;
  /** Field name as printed on the document. */
  label: string;
  type?: FieldType;
  hint?: string;
};

const KEY_PATTERN = /^[A-Za-z_][A-Za-z0-9_]{0,63}$/;

export function validateFields(fields: FieldSpec[] | undefined): FieldSpec[] {
  if (!fields || fields.length === 0) {
    throw new Error('Add at least one field to extract');
  }
  const seen = new Set<string>();
  for (const field of fields) {
    if (!field.key || !KEY_PATTERN.test(field.key)) {
      throw new Error(`Field key "${field.key}" must start with a letter or "_" and contain only letters, digits and "_"`);
    }
    if (!field.label?.trim()) {
      throw new Error(`Field "${field.key}" needs a label`);
    }
    if (seen.has(field.key)) {
      throw new Error(`Field key "${field.key}" is used twice`);
    }
    seen.add(field.key);
  }
  return fields;
}

/** The user turn of the completion; the rules are in the prompt setting's template. */
export const EXTRACTION_QUESTION = 'Extract the fields from the uploaded document.';

/**
 * Value of the prompt setting's `{fields}` variable, one line per field:
 * `- key: "label" (type) - hint` — the format the template's "Field list" line describes.
 */
export function buildFieldList(fields: FieldSpec[]): string {
  return fields
    .map((field) => {
      const hint = field.hint?.trim() ? ` - ${field.hint.trim()}` : '';
      return `- ${field.key}: "${field.label.trim()}" (${field.type ?? 'text'})${hint}`;
    })
    .join('\n');
}

/**
 * Every requested key is present in the result (null when not found); extra keys are dropped.
 * An answer without a JSON object is an error — never an all-null result that would look like
 * "the document had none of these fields".
 */
export function parseAnswer(answer: string, fields: FieldSpec[]): Record<string, string | null> {
  const start = answer.indexOf('{');
  const end = answer.lastIndexOf('}');
  let parsed: unknown;
  if (start >= 0 && end > start) {
    try {
      parsed = JSON.parse(answer.slice(start, end + 1));
    } catch {
      parsed = undefined;
    }
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`The model answer is not a JSON object: ${preview(answer)}`);
  }
  const obj = parsed as Record<string, unknown>;
  const values: Record<string, string | null> = {};
  for (const field of fields) {
    values[field.key] = scalar(obj[field.key]);
  }
  return values;
}

function scalar(value: unknown): string | null {
  if (value === null || value === undefined) {
    return null;
  }
  const text = typeof value === 'object' ? JSON.stringify(value) : String(value).trim();
  return text === '' ? null : text;
}

function preview(text: string): string {
  return text.length > 300 ? `${text.slice(0, 297)}...` : text;
}
