# OCR example — company information variant

The same Function Unit and Automation flow as `../` (FU code `receipt-notice-ocr-20261007-2tuhin`,
flow key `hermes-ocr-receipt-notice`), re-pointed at a company information form so the extraction can be
checked against a real test document. Version 1.0.2 of the FU replaces the nine receipt-notice fields with:

| key | label | type |
|---|---|---|
| `company_type` | Company Type (the ticked option) | text |
| `business_commencement_date` | Date of Business Commencement (printed DD/MM/YYYY) | date |
| `incorporation_country` | Country/Region / Jurisdiction of Incorporation / Registration | text |
| `office_telephone` | Office Telephone Number | text |
| `annual_sales_turnover` | Annual Sales Turnover | number |
| `email_address` | Email Address | text |

The flow writes the values back to these fields and also appends the raw extracted JSON to `ocr_message`,
so an all-empty result can be told apart from a key mismatch.

Import order in a target environment (same as `../README.md`): flow JSON in Admin Center → Automation Flow
Migration → Import Flow (json), same workspace as the existing flow; FU zip in Admin Center → Function Unit →
Import (new version), then Validate → Deploy. Where portal needs the DW tables (no Admin write-back), import the
same zip in Developer Workstation as well. To go back to the receipt notice, re-import the files in `../`.
