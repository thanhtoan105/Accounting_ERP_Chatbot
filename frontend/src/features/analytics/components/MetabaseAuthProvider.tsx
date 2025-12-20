import { type ReactNode } from 'react'
import { MetabaseProvider, defineMetabaseAuthConfig } from '@metabase/embedding-sdk-react'
import { getSsoToken } from '../services/analytics'
import type { MetabaseEmbedConfig } from '../types'

interface MetabaseAuthProviderProps {
  embedConfig: MetabaseEmbedConfig
  children: ReactNode
}

export function MetabaseAuthProvider({ embedConfig, children }: MetabaseAuthProviderProps) {
  const authConfig = defineMetabaseAuthConfig({
    metabaseInstanceUrl: embedConfig.metabaseInstanceUrl,
    fetchRequestToken: async () => {
      const response = await getSsoToken()
      return { jwt: response.jwt }
    },
  })

  return <MetabaseProvider authConfig={authConfig}>{children}</MetabaseProvider>
}
