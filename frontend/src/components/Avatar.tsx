import { useState } from 'react'
import { initials } from '../features/shell/initials'

type Props = {
  name: string
  /** From profilePhotoUrl or familyPhotoUrl. Null shows the initials. */
  photoUrl: string | null
  /** Size, colours, and text size of the circle, as each place already styles its initials. */
  className: string
}

/**
 * A round photo, or initials when there is none or it fails to load. Decorative: the name is
 * always written next to it, so it is hidden from screen readers.
 */
export function Avatar({ name, photoUrl, className }: Props) {
  const [failed, setFailed] = useState<string | null>(null)
  const showPhoto = photoUrl !== null && failed !== photoUrl

  return (
    <span aria-hidden="true" className={`grid shrink-0 place-items-center overflow-hidden rounded-full ${className}`}>
      {showPhoto ? (
        <img src={photoUrl} alt="" className="size-full object-cover" onError={() => setFailed(photoUrl)} />
      ) : (
        initials(name)
      )}
    </span>
  )
}
