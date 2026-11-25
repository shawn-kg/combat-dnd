const PREFIX = 'https://www.dndbeyond.com/characters/'

/** The server only stores links in this exact form; anything else is never turned into a link here. */
export const isDndBeyondLink = (url: string | null | undefined): url is string =>
  !!url && url.startsWith(PREFIX) && /^\d+$/.test(url.slice(PREFIX.length))
