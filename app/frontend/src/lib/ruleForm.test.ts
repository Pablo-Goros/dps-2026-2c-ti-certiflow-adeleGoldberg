import { describe, expect, it } from 'vitest'
import { newCriterionForm, newRuleForm, toRule, toSection, type SectionForm } from './ruleForm'
import { formatDate, localDateTimeToInstant } from './format'
import { blockerLabel, humanize, label } from './labels'
import { describeBand, parseNumber } from './rules'

describe('rule forms', () => {
  it('builds a yes/no rule, dropping the severity of an approved outcome', () => {
    const form = newRuleForm('YES_NO')
    form.yes.severity = 'HIGH'

    const rule = toRule(form)

    expect(rule.yes?.severity).toBeNull()
    expect(rule.no?.severity).toBe('MEDIUM')
  })

  it('builds an options rule keyed by option name', () => {
    const form = newRuleForm('OPTIONS')
    form.options[0].key = 'clean'
    form.options[1].key = 'dirty'

    expect(Object.keys(toRule(form).options!)).toEqual(['clean', 'dirty'])
  })

  it('refuses unnamed, repeated or too few options', () => {
    const form = newRuleForm('OPTIONS')
    expect(() => toRule(form)).toThrow(/nombre/)
    form.options[0].key = 'a'
    form.options[1].key = 'a'
    expect(() => toRule(form)).toThrow(/repetida/)
    form.options.pop()
    expect(() => toRule(form)).toThrow(/al menos dos/)
  })

  it('builds a numeric rule with open and closed band ends', () => {
    const form = newRuleForm('NUMERIC_RANGE')
    form.unit = 'c'
    form.minimum = '-50'
    form.maximum = '150,5'
    form.bands[0].lower = '2'
    form.bands[0].upper = ''

    const rule = toRule(form)

    expect(rule.minimum).toBe(-50)
    expect(rule.maximum).toBe(150.5)
    expect(rule.bands![0]).toMatchObject({ lower: 2, upper: null, lowerInclusive: true, upperInclusive: false })
  })

  it('requires the range limits of a numeric rule', () => {
    expect(() => toRule(newRuleForm('NUMERIC_RANGE'))).toThrow(/mínimo/)
  })

  it('builds a section and refuses repeated criterion ids', () => {
    const criterion = newCriterionForm()
    criterion.id = 'ELEC'
    const section: SectionForm = { name: ' Seguridad ', order: '1', criteria: [criterion] }

    expect(toSection(section)).toMatchObject({ name: 'Seguridad', order: 1, criteria: [{ id: 'ELEC', subsystem: null }] })
    expect(() => toSection({ ...section, criteria: [criterion, criterion] })).toThrow(/repetido/)
    expect(() => toSection({ ...section, criteria: [] })).toThrow(/al menos un criterio/)
  })

  it('keeps evidence requirements with at least one required item', () => {
    const criterion = newCriterionForm()
    criterion.id = 'DOC'
    criterion.evidence = [{ type: 'DOCUMENT', label: 'manual', mandatory: true, minimumCount: '0' }]

    const section = toSection({ name: 'S', order: '1', criteria: [criterion] })

    expect(section.criteria[0].evidence[0].minimumCount).toBe(1)
  })
})

describe('formatting helpers', () => {
  it('parses numbers with a decimal comma and rejects text', () => {
    expect(parseNumber('3,5')).toBe(3.5)
    expect(parseNumber('')).toBeNull()
    expect(parseNumber('abc')).toBeNull()
  })

  it('formats calendar dates without shifting the day', () => {
    expect(formatDate('2026-10-01')).toBe('1 oct 2026')
    expect(formatDate(null)).toBe('—')
  })

  it('converts a local date-time into an instant', () => {
    expect(localDateTimeToInstant('')).toBeUndefined()
    expect(localDateTimeToInstant('2030-01-01T10:00')).toMatch(/^20(29-12-31|30-01-01)T\d\d:00:00\.000Z$/)
  })

  it('describes bands with their open and closed ends', () => {
    expect(describeBand({ lower: 2, lowerInclusive: true, upper: 8, upperInclusive: false, outcome: { code: '', result: 'APPROVED', severity: null, description: '' } })).toBe('[2, 8)')
  })

  it('translates known codes and humanizes unknown ones', () => {
    expect(label('VALID')).toBe('Vigente')
    expect(label('SOME_NEW_CODE')).toBe('Some new code')
    expect(humanize('biosafetyLevel')).toBe('Biosafety level')
    expect(blockerLabel('InspectionNotClosed')).toMatch(/cerrada/)
    expect(blockerLabel('Brand_New')).toBe('Brand new')
  })
})
