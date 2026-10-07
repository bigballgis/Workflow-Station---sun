/// <reference types="vitest/globals" />

import { buildFieldList, parseAnswer, validateFields } from '../src/lib/common/field-prompt';

const FIELDS = [
  { key: 'receipt_number', label: 'Receipt Number', hint: '3 letters then 10 digits' },
  { key: 'received_date', label: 'Received Date', type: 'date' as const },
  { key: 'fee', label: 'Filing Fee', type: 'number' as const },
];

describe('buildFieldList', () => {
  test('one line per field with its type and hint, in the format the template describes', () => {
    expect(buildFieldList(FIELDS)).toBe([
      '- receipt_number: "Receipt Number" (text) - 3 letters then 10 digits',
      '- received_date: "Received Date" (date)',
      '- fee: "Filing Fee" (number)',
    ].join('\n'));
  });
});

describe('parseAnswer', () => {
  test('keeps requested keys only, in request order, missing ones as null', () => {
    const values = parseAnswer(
      '```json\n{"received_date":"2023-01-15","receipt_number":"MCT2373613541","extra":"x"}\n```',
      FIELDS,
    );
    expect(values).toEqual({ receipt_number: 'MCT2373613541', received_date: '2023-01-15', fee: null });
    expect(Object.keys(values)).toEqual(['receipt_number', 'received_date', 'fee']);
  });

  test('renders numbers as text and blank strings as null', () => {
    expect(parseAnswer('{"receipt_number":"  ","fee":470.5}', FIELDS)).toEqual({
      receipt_number: null, received_date: null, fee: '470.5',
    });
  });

  test('an answer without a JSON object is an error, not an all-null result', () => {
    expect(() => parseAnswer('Sorry, I cannot read this document.', FIELDS)).toThrow(/not a JSON object/);
    expect(() => parseAnswer('{not json}', FIELDS)).toThrow(/not a JSON object/);
  });
});

describe('validateFields', () => {
  test('rejects empty lists, bad or duplicate keys and missing labels', () => {
    expect(() => validateFields([])).toThrow(/at least one field/);
    expect(() => validateFields([{ key: '1bad', label: 'x' }])).toThrow(/must start with/);
    expect(() => validateFields([{ key: 'a', label: ' ' }])).toThrow(/needs a label/);
    expect(() => validateFields([{ key: 'a', label: 'A' }, { key: 'a', label: 'B' }])).toThrow(/used twice/);
  });
});
