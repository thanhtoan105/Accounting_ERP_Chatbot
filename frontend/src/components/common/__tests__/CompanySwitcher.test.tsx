import { render } from '@testing-library/react'
import { describe, it } from 'vitest'
import CompanySwitcher from '../../common/CompanySwitcher'

describe('CompanySwitcher', () => {
  it('renders without crashing', () => {
    render(<CompanySwitcher />)
  })
})
