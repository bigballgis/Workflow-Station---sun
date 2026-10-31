import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Field from '../DesignedDateTimeField.vue'

describe('designed date/time values', () => {
  it('passes a range array through unchanged and preserves its configured formats', () => {
    const wrapper = mount(Field, { props: { kind: 'daterange', modelValue: null, valueFormat: 'YYYY/MM/DD', format: 'DD/MM/YYYY' } })
    const picker = wrapper.findComponent({ name: 'ElDatePicker' })
    expect(picker.props()).toMatchObject({ type: 'daterange', valueFormat: 'YYYY/MM/DD', format: 'DD/MM/YYYY' })
    const dates = ['2026/10/01', '2026/10/31']
    picker.vm.$emit('update:modelValue', dates)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([dates])
  })
  it('uses a time picker for time values and forwards change without coercing it to a date', () => {
    const wrapper = mount(Field, { props: { kind: 'time', modelValue: '12:30:00', disabled: true } })
    const picker = wrapper.findComponent({ name: 'ElTimePicker' })
    expect(picker.props()).toMatchObject({ valueFormat: 'HH:mm:ss', disabled: true })
    picker.vm.$emit('change', '13:00:00')
    expect(wrapper.emitted('change')?.[0]).toEqual(['13:00:00'])
  })
})
