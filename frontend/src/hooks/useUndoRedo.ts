import { useCallback, useRef, useState } from 'react'

type Updater<T> = T | ((previous: T) => T)

export interface UndoRedoState<T> {
  value: T
  setValue: (updater: Updater<T>) => void
  reset: (nextValue: T) => void
  canUndo: boolean
  canRedo: boolean
  undo: () => void
  redo: () => void
}

const DEFAULT_LIMIT = 50

function cloneValue<T>(value: T): T {
  if (typeof structuredClone === 'function') {
    return structuredClone(value)
  }
  return JSON.parse(JSON.stringify(value)) as T
}

export function useUndoRedo<T>(initialValue: T, limit: number = DEFAULT_LIMIT): UndoRedoState<T> {
  const [value, setValue] = useState<T>(initialValue)
  const pastRef = useRef<T[]>([])
  const futureRef = useRef<T[]>([])

  const updateValue = useCallback(
    (updater: Updater<T>) => {
      setValue((current) => {
        const next = typeof updater === 'function' ? (updater as (prev: T) => T)(current) : updater
        if (Object.is(next, current)) {
          return current
        }

        pastRef.current.push(cloneValue(current))
        if (pastRef.current.length > limit) {
          pastRef.current.shift()
        }
        futureRef.current = []
        return next
      })
    },
    [limit],
  )

  const undo = useCallback(() => {
    setValue((current) => {
      const previous = pastRef.current.pop()
      if (previous === undefined) {
        return current
      }

      futureRef.current.push(cloneValue(current))
      return previous
    })
  }, [])

  const redo = useCallback(() => {
    setValue((current) => {
      const next = futureRef.current.pop()
      if (next === undefined) {
        return current
      }

      pastRef.current.push(cloneValue(current))
      return next
    })
  }, [])

  const reset = useCallback((nextValue: T) => {
    pastRef.current = []
    futureRef.current = []
    setValue(nextValue)
  }, [])

  return {
    value,
    setValue: updateValue,
    reset,
    canUndo: pastRef.current.length > 0,
    canRedo: futureRef.current.length > 0,
    undo,
    redo,
  }
}

export default useUndoRedo

