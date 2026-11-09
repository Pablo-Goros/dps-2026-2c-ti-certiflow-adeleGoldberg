// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Card } from './ui'

afterEach(cleanup)

describe('Card', () => {
  it('collapses and expands its content, keeping what was typed', () => {
    render(<Card title="Presión"><input aria-label="Medición" /></Card>)
    const input = screen.getByLabelText('Medición') as HTMLInputElement
    fireEvent.change(input, { target: { value: '9' } })

    fireEvent.click(screen.getByRole('button', { name: 'Contraer «Presión»' }))

    expect(input.closest('.card-body')?.hasAttribute('hidden')).toBe(true)
    expect(screen.getByRole('button', { name: 'Desplegar «Presión»' }).getAttribute('aria-expanded')).toBe('false')

    fireEvent.click(screen.getByRole('heading', { name: 'Presión' }))

    expect(input.closest('.card-body')?.hasAttribute('hidden')).toBe(false)
    expect(input.value).toBe('9')
  })

  it('has nothing to collapse without a title', () => {
    render(<Card><p>Contenido</p></Card>)

    expect(screen.queryByRole('button')).toBeNull()
  })
})
