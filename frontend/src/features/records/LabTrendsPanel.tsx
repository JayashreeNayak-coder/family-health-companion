import { useEffect, useState, type PointerEvent } from 'react'
import { errorMessage } from '../../api/client'
import { getTrend, listTrendSeries, type Trend, type TrendSeries } from '../../api/labs'
import { Card } from '../../components/Card'
import { EmptyState, ErrorNote, Loading } from '../../components/Feedback'
import { inputClass } from '../../components/formStyles'

type Props = { profileId: string }

function seriesId(series: TrendSeries) {
  return `${series.testKey}\u0000${series.unit ?? ''}`
}

function unitLabel(unit: string | null) {
  return unit ?? 'no unit recorded'
}

/**
 * Values over time for one test and unit, exactly as recorded. No reference lines or colours
 * suggest whether a value is good or bad: interpretation is for a clinician.
 */
export function LabTrendsPanel({ profileId }: Props) {
  const [series, setSeries] = useState<TrendSeries[] | null>(null)
  const [selected, setSelected] = useState('')
  const [trend, setTrend] = useState<Trend | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false
    listTrendSeries(profileId).then(
      (found) => {
        if (!ignore) {
          setSeries(found)
          setSelected(found[0] ? seriesId(found[0]) : '')
        }
      },
      (failure) => {
        if (!ignore) {
          setError(errorMessage(failure))
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [profileId])

  const current = series?.find((candidate) => seriesId(candidate) === selected)

  useEffect(() => {
    if (!current) {
      return
    }
    let ignore = false
    getTrend(profileId, current.testKey, current.unit).then(
      (found) => {
        if (!ignore) {
          setTrend(found)
        }
      },
      (failure) => {
        if (!ignore) {
          setError(errorMessage(failure))
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [profileId, current])

  return (
    <Card title="Lab trends" description="Values over time for one test and unit, exactly as recorded.">
      <ErrorNote message={error} />
      {!series && !error && <Loading lines={3} />}
      {series && series.length === 0 && (
        <EmptyState title="No trends yet">A trend needs lab results with a number and a date.</EmptyState>
      )}
      {series && series.length > 0 && (
        <>
          <label className="block max-w-md text-sm font-medium text-slate-800">
            Test
            <select
              className={inputClass}
              value={selected}
              onChange={(event) => setSelected(event.target.value)}
            >
              {series.map((option) => (
                <option key={seriesId(option)} value={seriesId(option)}>
                  {option.testName} ({unitLabel(option.unit)}) · {option.count}{' '}
                  {option.count === 1 ? 'value' : 'values'}
                </option>
              ))}
            </select>
          </label>
          {current && trend && trend.testKey === current.testKey && trend.unit === current.unit && (
            <TrendView name={current.testName} trend={trend} />
          )}
        </>
      )}
    </Card>
  )
}

function TrendView({ name, trend }: { name: string; trend: Trend }) {
  const unit = unitLabel(trend.unit)
  const latest = trend.points[trend.points.length - 1]
  return (
    <div className="mt-5 space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h4 className="font-semibold text-slate-900">
          {name} <span className="font-normal text-slate-500">({unit})</span>
        </h4>
        {latest && (
          <p className="text-right text-sm text-slate-600">
            Latest{' '}
            <span className="text-xl font-semibold text-slate-900 tabular-nums">{latest.value}</span>
            {trend.unit && <span className="text-slate-700"> {trend.unit}</span>}
            <span className="block text-xs text-slate-500">on {latest.resultOn}</span>
          </p>
        )}
      </div>
      {trend.points.length >= 2 && <LineChart trend={trend} name={name} />}
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <caption className="sr-only">
            {name} values in {unit}, oldest first
          </caption>
          <thead>
            <tr className="border-b border-slate-200 text-xs text-slate-500">
              <th className="py-2 font-medium">Date</th>
              <th className="py-2 text-right font-medium">Value ({unit})</th>
            </tr>
          </thead>
          <tbody>
            {trend.points.map((point) => (
              <tr key={point.id} className="border-b border-slate-100 last:border-0">
                <td className="py-2 text-slate-700">{point.resultOn}</td>
                <td className="py-2 text-right text-slate-900 tabular-nums">{point.value}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {trend.excludedCount > 0 && (
        <p className="text-sm text-slate-600">
          {trend.excludedCount} other {trend.excludedCount === 1 ? 'result' : 'results'} for this test
          {trend.excludedCount === 1 ? ' isn’t' : ' aren’t'} shown, because of a different unit, a
          missing date, or a value that isn’t a number. Units are never converted.
        </p>
      )}
    </div>
  )
}

const WIDTH = 640
const HEIGHT = 220
const PAD = { top: 16, right: 20, bottom: 28, left: 52 }

/** Three evenly spaced gridline values across the range, rounded for reading. */
function gridValues(min: number, max: number): number[] {
  return [min, (min + max) / 2, max].map((value) => Number(value.toPrecision(4)))
}

/**
 * One series, so no legend: the heading names it. No reference band or colour says whether a
 * value is good or bad, because interpretation is for a clinician. Hover anywhere on the plot:
 * the crosshair snaps to the nearest date. The table below is the accessible view of every value.
 */
function LineChart({ trend, name }: { trend: Trend; name: string }) {
  const [active, setActive] = useState<number | null>(null)
  const times = trend.points.map((point) => Date.parse(point.resultOn))
  const values = trend.points.map((point) => point.value)
  const [minTime, maxTime] = [Math.min(...times), Math.max(...times)]
  let [minValue, maxValue] = [Math.min(...values), Math.max(...values)]
  if (minValue === maxValue) {
    minValue -= 1
    maxValue += 1
  }
  const x = (time: number) =>
    PAD.left + ((time - minTime) / (maxTime - minTime || 1)) * (WIDTH - PAD.left - PAD.right)
  const y = (value: number) =>
    PAD.top + (1 - (value - minValue) / (maxValue - minValue)) * (HEIGHT - PAD.top - PAD.bottom)
  const path = trend.points
    .map((point, index) => `${index === 0 ? 'M' : 'L'}${x(times[index])},${y(point.value)}`)
    .join(' ')
  const first = trend.points[0]
  const last = trend.points[trend.points.length - 1]

  function handleMove(event: PointerEvent<SVGRectElement>) {
    const box = event.currentTarget.ownerSVGElement?.getBoundingClientRect()
    if (!box || box.width === 0) {
      return
    }
    const pointerX = ((event.clientX - box.left) / box.width) * WIDTH
    let nearest = 0
    times.forEach((time, index) => {
      if (Math.abs(x(time) - pointerX) < Math.abs(x(times[nearest]) - pointerX)) {
        nearest = index
      }
    })
    setActive(nearest)
  }

  const point = active === null ? null : trend.points[active]
  const pointX = active === null ? 0 : x(times[active])

  return (
    <div className="relative w-full max-w-2xl">
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        className="w-full touch-pan-y"
        role="img"
        aria-label={`${name} from ${first.resultOn} to ${last.resultOn}. The table below lists every value.`}
      >
        {gridValues(minValue, maxValue).map((value) => (
          <g key={value}>
            <line x1={PAD.left} x2={WIDTH - PAD.right} y1={y(value)} y2={y(value)} className="stroke-slate-200" />
            <text
              x={PAD.left - 8}
              y={y(value)}
              textAnchor="end"
              dominantBaseline="middle"
              className="fill-slate-500 text-[11px] tabular-nums"
            >
              {value}
            </text>
          </g>
        ))}
        <text x={PAD.left} y={HEIGHT - 8} className="fill-slate-500 text-[11px]">
          {first.resultOn}
        </text>
        <text x={WIDTH - PAD.right} y={HEIGHT - 8} textAnchor="end" className="fill-slate-500 text-[11px]">
          {last.resultOn}
        </text>
        {point && (
          <line
            x1={pointX}
            x2={pointX}
            y1={PAD.top}
            y2={HEIGHT - PAD.bottom}
            strokeDasharray="3 3"
            className="stroke-slate-400"
          />
        )}
        <path
          d={path}
          fill="none"
          strokeWidth={2}
          strokeLinejoin="round"
          strokeLinecap="round"
          className="stroke-brand-600"
        />
        {trend.points.map((item, index) => (
          <circle
            key={item.id}
            cx={x(times[index])}
            cy={y(item.value)}
            r={active === index ? 6 : 4}
            strokeWidth={2}
            className="fill-brand-600 stroke-white"
          />
        ))}
        {/* The whole plot is the hit target, so the reader aims at a date, not a 2px line. */}
        <rect
          x={PAD.left - 12}
          y={0}
          width={WIDTH - PAD.left - PAD.right + 24}
          height={HEIGHT - PAD.bottom}
          fill="transparent"
          onPointerMove={handleMove}
          onPointerDown={handleMove}
          onPointerLeave={() => setActive(null)}
        />
      </svg>
      {point && (
        <div
          aria-hidden="true"
          className="pointer-events-none absolute top-0 z-10 -translate-x-1/2 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs whitespace-nowrap shadow-md"
          style={{ left: `${Math.min(Math.max((pointX / WIDTH) * 100, 12), 88)}%` }}
        >
          <span className="block text-slate-500">{point.resultOn}</span>
          <span className="font-semibold text-slate-900 tabular-nums">
            {point.value}
            {trend.unit ? ` ${trend.unit}` : ''}
          </span>
        </div>
      )}
    </div>
  )
}
