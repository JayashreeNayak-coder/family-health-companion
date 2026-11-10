import qrcode from 'qrcode-generator'
import { useMemo } from 'react'

/**
 * A QR code drawn as SVG from the library's module grid, so no generated markup is injected.
 * Dark on white with a quiet zone, whatever the theme, so phone cameras can read it.
 */
export function QrCode({ value, label, size = 192 }: { value: string; label: string; size?: number }) {
  const { count, path } = useMemo(() => {
    const code = qrcode(0, 'M')
    code.addData(value)
    code.make()
    const modules = code.getModuleCount()
    let d = ''
    for (let row = 0; row < modules; row++) {
      for (let col = 0; col < modules; col++) {
        if (code.isDark(row, col)) {
          d += `M${col + 4} ${row + 4}h1v1h-1z`
        }
      }
    }
    return { count: modules + 8, path: d }
  }, [value])

  return (
    <svg
      role="img"
      aria-label={label}
      viewBox={`0 0 ${count} ${count}`}
      width={size}
      height={size}
      shapeRendering="crispEdges"
      className="rounded-md border border-slate-200"
    >
      <rect width={count} height={count} fill="#ffffff" />
      <path d={path} fill="#0f172a" />
    </svg>
  )
}
