# OCR example: flow + Function Unit for environment migration

Exported from dev on 2026-10-07 as a migration sample (no credentials, no personal data):

| File | What | Import in the target environment |
|---|---|---|
| `flow-hermes-ocr-receipt-notice.json` | Automation flow `hermes-ocr-receipt-notice` (Webhook → Content Organizer · Extract Fields **1.0.2** → Code → Return Response) | Admin Center → **Automation Flows** → Import (publish). Import it **before** the FU |
| `fu-receipt-notice-ocr.zip` | Function Unit "Receipt Notice OCR" (`receipt-notice-ocr-20261007-2tuhin`): BPMN, table, 3 forms, views | Admin Center → **Function Units** → Import → Validate → Deploy → Access (grant a role) |

The BPMN service task references the flow by its business key (`ap:flowKey = hermes-ocr-receipt-notice`), which the
engine resolves to the target environment's flow id at deploy time, so neither file needs editing.

Prerequisites in the target environment: content-organizer piece 1.0.2 installed, Content Organizer configuration and
`IB2B_SECRET` in place, and the smoke test passing — see [content-organizer-ocr-guide.md](../../content-organizer-ocr-guide.md) §8.

The main table's `id` is generated as `RN-yyyyMMdd-NNNN` (daily reset) and configured as the Request ID, so the
portal's Request ID column shows e.g. `RN-20261008-0001` instead of `-`.

The real Content Organizer only accepts PDFs that carry a valid classification label; the dev sample
(`Receipt Notice.pdf`, not in the repo) has none, so use a labelled PDF in UAT and above.
