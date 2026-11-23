/** Whole numbers only; anything else counts as not entered yet. */
export function parseInitiative(text: string | undefined): number | null {
  if (text === undefined || text.trim() === '') return null
  const n = Number(text)
  return Number.isInteger(n) ? n : null
}
