/**
 * Format date string to dd/mm/yyyy format
 * @param dateString - Date string in ISO format or any valid date string
 * @returns Formatted date string in dd/mm/yyyy format
 */
export function formatDateDDMMYYYY(dateString: string | Date | null | undefined): string {
  if (!dateString) return ''

  try {
    const date = typeof dateString === 'string' ? new Date(dateString) : dateString

    // Check if date is valid
    if (isNaN(date.getTime())) return ''

    const day = String(date.getDate()).padStart(2, '0')
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const year = date.getFullYear()

    return `${day}/${month}/${year}`
  } catch {
    return ''
  }
}

/**
 * Convert date string to yyyy-mm-dd format (for HTML date input)
 * @param dateString - Date string in any format
 * @returns Date string in yyyy-mm-dd format for HTML date input
 */
export function formatDateForInput(dateString: string | Date | null | undefined): string {
  if (!dateString) return ''

  try {
    const date = typeof dateString === 'string' ? new Date(dateString) : dateString

    // Check if date is valid
    if (isNaN(date.getTime())) return ''

    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')

    return `${year}-${month}-${day}`
  } catch {
    return ''
  }
}

/**
 * Parse dd/mm/yyyy format to Date object
 * @param dateString - Date string in dd/mm/yyyy format
 * @returns Date object or null if invalid
 */
export function parseDateDDMMYYYY(dateString: string): Date | null {
  if (!dateString) return null

  try {
    const parts = dateString.split('/')
    if (parts.length !== 3) return null

    const day = parseInt(parts[0], 10)
    const month = parseInt(parts[1], 10) - 1 // Month is 0-indexed
    const year = parseInt(parts[2], 10)

    const date = new Date(year, month, day)

    // Validate the date was parsed correctly
    if (date.getDate() === day && date.getMonth() === month && date.getFullYear() === year) {
      return date
    }

    return null
  } catch {
    return null
  }
}
