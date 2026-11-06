import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import { getTrend, listTrendSeries, type Trend, type TrendSeries } from '../../api/labs'
import { Card, ErrorNote, inputClass } from '../../components/ui'

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
    <Card title="Lab trends">
      <ErrorNote message={error} />
      {!series && !error && <p role="status">Loading…</p>}
      {series && series.length === 0 && (
        <p className="text-sm text-slate-600">
          A trend needs lab results with a number and a date.
        </p>
      )}
      {series && series.length > 0 && (
        <>
          <label className="block text-sm font-medium">
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
  return (
    <div className="mt-4 space-y-3">
      <h4 className="font-medium">
        {name} <span className="font-normal text-slate-600">({unit})</span>
      </h4>
      {trend.points.length >= 2 && <LineChart trend={trend} name={name} />}
      <table className="w-full text-left text-sm">
        <caption className="sr-only">
          {name} values in {unit}, oldest first
        </caption>
        <thead>
          <tr className="border-b border-slate-200 text-slate-600">
            <th className="py-1 font-medium">Date</th>
            <th className="py-1 font-medium">Value ({unit})</th>
          </tr>
        </thead>
        <tbody>
          {trend.points.map((point) => (
            <tr key={point.id} className="border-b border-slate-100">
              <td className="py-1">{point.resultOn}</td>
              <td className="py-1 tabular-nums">{point.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
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

const WIDTH = 560
const HEIGHT = 200
const PAD = { top: 16, right: 16, bottom: 28, left: 56 }

/** One series, so no legend: the heading names it. Hover a point for its date and value. */
function LineChart({ trend, name }: { trend: Trend; name: string }) {
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

  return (
    <svg
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      className="w-full max-w-xl"
      role="img"
      aria-label={`${name} from ${first.resultOn} to ${last.resultOn}. The table below lists every value.`}
    >
      {[minValue, maxValue].map((value) => (
        <g key={value}>
          <line
            x1={PAD.left}
            x2={WIDTH - PAD.right}
            y1={y(value)}
            y2={y(value)}
            className="stroke-slate-200"
          />
          <text
            x={PAD.left - 8}
            y={y(value)}
            textAnchor="end"
            dominantBaseline="middle"
            className="fill-slate-500 text-[11px]"
          >
            {Number(value.toFixed(3))}
          </text>
        </g>
      ))}
      <text x={PAD.left} y={HEIGHT - 8} className="fill-slate-500 text-[11px]">
        {first.resultOn}
      </text>
      <text x={WIDTH - PAD.right} y={HEIGHT - 8} textAnchor="end" className="fill-slate-500 text-[11px]">
        {last.resultOn}
      </text>
      <path d={path} fill="none" strokeWidth={2} strokeLinejoin="round" className="stroke-teal-700" />
      {trend.points.map((point, index) => (
        <g key={point.id}>
          <circle
            cx={x(times[index])}
            cy={y(point.value)}
            r={4}
            strokeWidth={2}
            className="fill-teal-700 stroke-white"
          />
          {/* A larger invisible target, so the tooltip is easy to reach. */}
          <circle cx={x(times[index])} cy={y(point.value)} r={12} fill="transparent">
            <title>
              {point.resultOn}: {point.value} {trend.unit ?? ''}
            </title>
          </circle>
        </g>
      ))}
    </svg>
  )
}
