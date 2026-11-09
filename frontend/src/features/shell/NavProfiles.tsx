import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { listProfiles, type Profile } from '../../api/profiles'

type NavProfiles = {
  /** The profiles this account can open, or null until the first load. */
  profiles: Profile[] | null
  /** Pages call this after adding, renaming, or deleting a profile, so the sidebar follows. */
  reload: () => void
}

const Context = createContext<NavProfiles>({ profiles: null, reload: () => {} })

export function NavProfilesProvider({ children }: { children: ReactNode }) {
  const [profiles, setProfiles] = useState<Profile[] | null>(null)
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    let ignore = false
    listProfiles().then(
      (result) => {
        if (!ignore) {
          setProfiles(result.items)
        }
      },
      // The sidebar is a shortcut. The page shows its own error if the list can't load.
      () => {
        if (!ignore) {
          setProfiles((current) => current ?? [])
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [revision])

  const reload = useCallback(() => setRevision((current) => current + 1), [])

  return <Context.Provider value={{ profiles, reload }}>{children}</Context.Provider>
}

export function useNavProfiles(): NavProfiles {
  return useContext(Context)
}
