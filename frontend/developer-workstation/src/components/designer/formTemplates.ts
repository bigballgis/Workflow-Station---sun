/**
 * Built-in form templates for "Create from Template" in FormDesigner.
 *
 * Templates are stored as TypeScript constants (no backend API needed).
 * Each template provides a pre-populated configJson that can be loaded
 * directly into the fc-designer.
 */
import type { FormBusinessLogicConfig } from './formBusinessLogicTypes'

export interface FormTemplate {
  id: string
  name: string                         // i18n key
  description: string                  // i18n key
  thumbnail: string                    // placeholder for future preview image
  configJson: FormBusinessLogicConfig  // pre-populated form configuration
}

export const BUILT_IN_TEMPLATES: FormTemplate[] = [
  {
    id: 'basic-info',
    name: 'template.basicInfo',
    description: 'template.basicInfoDesc',
    thumbnail: '',
    configJson: {
      rule: [
        {
          type: 'input',
          field: 'name',
          title: 'Name',
          props: { placeholder: 'Enter name' },
          validate: [{ required: true, message: 'Name is required' }],
        },
        {
          type: 'input',
          field: 'description',
          title: 'Description',
          props: { type: 'textarea', rows: 3, placeholder: 'Enter description' },
        },
        {
          type: 'datePicker',
          field: 'date',
          title: 'Date',
          props: { type: 'date', placeholder: 'Select date' },
        },
      ],
      options: {},
      subForms: {},
    },
  },
  {
    id: 'approval-form',
    name: 'template.approvalForm',
    description: 'template.approvalFormDesc',
    thumbnail: '',
    configJson: {
      rule: [
        {
          type: 'input',
          field: 'applicant',
          title: 'Applicant',
          props: { placeholder: 'Enter applicant name' },
          validate: [{ required: true, message: 'Applicant is required' }],
        },
        {
          type: 'select',
          field: 'department',
          title: 'Department',
          props: { placeholder: 'Select department' },
          options: [
            { label: 'Engineering', value: 'engineering' },
            { label: 'Finance', value: 'finance' },
            { label: 'HR', value: 'hr' },
            { label: 'Marketing', value: 'marketing' },
          ],
          validate: [{ required: true, message: 'Department is required' }],
        },
        {
          type: 'input',
          field: 'reason',
          title: 'Reason',
          props: { type: 'textarea', rows: 4, placeholder: 'Enter reason' },
          validate: [{ required: true, message: 'Reason is required' }],
        },
        {
          type: 'inputNumber',
          field: 'amount',
          title: 'Amount',
          props: { min: 0, precision: 2, placeholder: 'Enter amount' },
        },
      ],
      options: {},
      subForms: {},
    },
  },
  {
    id: 'data-entry',
    name: 'template.dataEntry',
    description: 'template.dataEntryDesc',
    thumbnail: '',
    configJson: {
      rule: [
        {
          type: 'el-tabs',
          props: { type: 'border-card' },
          children: [
            {
              type: 'el-tab-pane',
              props: { label: 'Basic Fields' },
              children: [
                {
                  type: 'input',
                  field: 'title',
                  title: 'Title',
                  props: { placeholder: 'Enter title' },
                  validate: [{ required: true, message: 'Title is required' }],
                },
                {
                  type: 'input',
                  field: 'content',
                  title: 'Content',
                  props: { type: 'textarea', rows: 4, placeholder: 'Enter content' },
                },
                {
                  type: 'inputNumber',
                  field: 'quantity',
                  title: 'Quantity',
                  props: { min: 0, placeholder: 'Enter quantity' },
                },
                {
                  type: 'datePicker',
                  field: 'effectiveDate',
                  title: 'Effective Date',
                  props: { type: 'date', placeholder: 'Select date' },
                },
              ],
            },
            {
              type: 'el-tab-pane',
              props: { label: 'Additional Info' },
              children: [
                {
                  type: 'select',
                  field: 'category',
                  title: 'Category',
                  props: { placeholder: 'Select category' },
                  options: [
                    { label: 'Category A', value: 'a' },
                    { label: 'Category B', value: 'b' },
                    { label: 'Category C', value: 'c' },
                  ],
                },
                {
                  type: 'switch',
                  field: 'isActive',
                  title: 'Active',
                  props: {},
                },
                {
                  type: 'input',
                  field: 'remarks',
                  title: 'Remarks',
                  props: { type: 'textarea', rows: 3, placeholder: 'Enter remarks' },
                },
              ],
            },
          ],
        },
      ],
      options: {},
      subForms: {},
    },
  },
]
