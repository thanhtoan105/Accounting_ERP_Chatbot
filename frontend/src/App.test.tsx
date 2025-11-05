import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import App from './App'

describe('App', () => {
  it('renders the main heading', () => {
    render(
      <BrowserRouter>
        <App />
      </BrowserRouter>,
    )
    // App renders Login page by default, which has "Login" heading
    const heading = screen.getByRole('heading', { name: /login/i })
    expect(heading).toBeInTheDocument()
  })
})
