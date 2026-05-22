import { useState } from 'react'

interface AntDigLoaderProps {
  fullScreen?: boolean
  message?: string
}

export default function AntDigLoader({
  fullScreen = false,
  message = 'AutoRoot-X is loading...',
}: AntDigLoaderProps) {
  const videoSrc = '/antdigging.mp4'
  const spriteSrc = '/loader.png'
  const [videoError, setVideoError] = useState(false)
  const [spriteError, setSpriteError] = useState(false)

  const wrapperClass = fullScreen
    ? 'fixed inset-0 z-[70] flex items-center justify-center bg-background/95 backdrop-blur-sm'
    : 'flex items-center justify-center'

  return (
    <div className={wrapperClass}>
      <div className="flex flex-col items-center gap-3 px-6 py-5">
        <div className="relative h-60 w-40 overflow-hidden rounded-xl bg-transparent">
          {!videoError ? (
            <video
              className="h-full w-full object-cover"
              autoPlay
              loop
              muted
              playsInline
              preload="auto"
              onError={() => setVideoError(true)}
            >
              <source src={videoSrc} type="video/mp4" />
            </video>
          ) : !spriteError ? (
            <>
              <img
                src={spriteSrc}
                alt=""
                aria-hidden="true"
                className="hidden"
                onError={() => setSpriteError(true)}
              />
              <img
                src={spriteSrc}
                role="img"
                aria-label="Ant digging"
                className="h-full w-full object-cover"
                alt="Ant digging"
              />
            </>
          ) : (
            <div className="h-full w-full" aria-hidden="true" />
          )}
        </div>
        <div className="text-center leading-relaxed">
          <p className="text-sm font-medium text-foreground">{message}</p>
        </div>
      </div>
    </div>
  )
}