import { Eye, EyeOff } from 'lucide-react'
import { useState, type InputHTMLAttributes } from 'react'
import { inputClass } from './formStyles'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'className'>

/**
 * A password field with a button that shows or hides what was typed. The button keeps one name,
 * "Show password", and reports whether it is on with aria-pressed.
 */
export function PasswordInput(props: Props) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="relative mt-1">
      <input {...props} type={visible ? 'text' : 'password'} className={`${inputClass.replace('mt-1 ', '')} pr-12`} />
      <button
        type="button"
        onClick={() => setVisible((current) => !current)}
        aria-label="Show password"
        aria-pressed={visible}
        className="absolute inset-y-0 right-0 grid w-11 place-items-center rounded-r-lg text-slate-500 hover:text-slate-800 focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-brand-600"
      >
        {visible ? <EyeOff aria-hidden="true" className="size-5" /> : <Eye aria-hidden="true" className="size-5" />}
      </button>
    </div>
  )
}
