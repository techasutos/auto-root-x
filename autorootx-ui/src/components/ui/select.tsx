import { Check, ChevronDown } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'

interface SelectProps {
  value?: string
  onChange: (value: string) => void
  options: string[]
  className?: string
  ariaLabel?: string
}

export function Select({ value, onChange, options, className = '', ariaLabel }: SelectProps) {
  const rootRef = useRef<HTMLDivElement | null>(null)
  const [open, setOpen] = useState(false)

  const selectedValue = value ?? options[0] ?? ''
  const selectedIndex = useMemo(
    () => options.findIndex((option) => option === selectedValue),
    [options, selectedValue]
  )

  useEffect(() => {
    function onWindowPointerDown(event: MouseEvent) {
      if (!rootRef.current) {
        return
      }
      if (!rootRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }

    window.addEventListener('mousedown', onWindowPointerDown)
    return () => window.removeEventListener('mousedown', onWindowPointerDown)
  }, [])

  function choose(option: string) {
    onChange(option)
    setOpen(false)
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      const next = options[(selectedIndex + 1 + options.length) % options.length]
      if (next) choose(next)
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      const next = options[(selectedIndex - 1 + options.length) % options.length]
      if (next) choose(next)
      return
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      setOpen((prev) => !prev)
      return
    }
    if (event.key === 'Escape') {
      setOpen(false)
    }
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        role="combobox"
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-label={ariaLabel}
        onClick={() => setOpen((prev) => !prev)}
        onKeyDown={onKeyDown}
        className={`w-full rounded-lg border border-input/80 bg-gradient-to-b from-muted/60 to-muted/30 px-3 py-2 pr-11 text-left text-sm text-foreground shadow-[inset_0_1px_0_rgba(255,255,255,0.06),0_10px_20px_rgba(0,0,0,0.22)] outline-none transition duration-200 hover:border-primary/40 focus:border-primary/70 focus:ring-2 focus:ring-ring/45 ${className}`}
      >
        <span>{selectedValue}</span>
        <span className="pointer-events-none absolute right-2 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-md border border-border/60 bg-muted/75 shadow-[inset_0_1px_0_rgba(255,255,255,0.05)]">
          <ChevronDown className={`h-3.5 w-3.5 text-muted-foreground transition-transform ${open ? 'rotate-180' : ''}`} />
        </span>
      </button>

      {open && (
        <div className="absolute z-50 mt-2 w-full rounded-2xl border border-border/80 bg-gradient-to-b from-card to-muted/80 p-2 shadow-[0_16px_30px_rgba(0,0,0,0.4)] backdrop-blur-sm">
          <ul role="listbox" aria-label={ariaLabel} className="space-y-1">
            {options.map((option) => {
              const selected = option === selectedValue
              return (
                <li key={option} role="option" aria-selected={selected}>
                  <button
                    type="button"
                    onClick={() => choose(option)}
                    className={`flex w-full items-center justify-between rounded-xl px-3 py-2 text-left text-sm transition ${selected ? 'bg-primary/20 text-primary border border-primary/35' : 'border border-transparent hover:bg-muted/70 hover:border-border/70'}`}
                  >
                    <span>{option}</span>
                    {selected && <Check className="h-3.5 w-3.5" />}
                  </button>
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </div>
  )
}