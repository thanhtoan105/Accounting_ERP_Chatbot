import { useCallback, useState } from 'react'
import { Upload, X, FileText, Image as ImageIcon, AlertCircle, Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'

export interface AttachmentFile {
  id: string
  file: File
  preview?: string
  status: 'pending' | 'uploading' | 'success' | 'error'
  error?: string
}

interface VoucherAttachmentDropzoneProps {
  voucherId: string | null
  onUploadSuccess?: (file: AttachmentFile) => void
  onUploadError?: (file: AttachmentFile, error: string) => void
  disabled?: boolean
  maxSize?: number // in bytes, default 10MB
  acceptedTypes?: string[] // MIME types, default ['image/*', 'application/pdf']
}

const DEFAULT_MAX_SIZE = 10 * 1024 * 1024 // 10MB
const DEFAULT_ACCEPTED_TYPES = ['image/*', 'application/pdf']

export function VoucherAttachmentDropzone({
  voucherId,
  onUploadSuccess,
  onUploadError,
  disabled = false,
  maxSize = DEFAULT_MAX_SIZE,
  acceptedTypes = DEFAULT_ACCEPTED_TYPES,
}: VoucherAttachmentDropzoneProps) {
  const [files, setFiles] = useState<AttachmentFile[]>([])
  const [isDragging, setIsDragging] = useState(false)
  const [dragError, setDragError] = useState<string | null>(null)

  const validateFile = useCallback(
    (file: File): string | null => {
      // Check file type
      const isValidType = acceptedTypes.some((type) => {
        if (type.endsWith('/*')) {
          const baseType = type.split('/')[0]
          return file.type.startsWith(`${baseType}/`)
        }
        return file.type === type
      })

      if (!isValidType) {
        return `Invalid file type. Only ${acceptedTypes.join(', ')} are allowed.`
      }

      // Check file size
      if (file.size > maxSize) {
        const maxSizeMB = (maxSize / (1024 * 1024)).toFixed(0)
        return `File size exceeds maximum allowed size of ${maxSizeMB}MB.`
      }

      return null
    },
    [acceptedTypes, maxSize],
  )

  const createPreview = useCallback((file: File): Promise<string | undefined> => {
    return new Promise((resolve) => {
      if (file.type.startsWith('image/')) {
        const reader = new FileReader()
        reader.onload = (e) => resolve(e.target?.result as string)
        reader.onerror = () => resolve(undefined)
        reader.readAsDataURL(file)
      } else {
        resolve(undefined)
      }
    })
  }, [])

  const handleFiles = useCallback(
    async (fileList: FileList | File[]) => {
      const fileArray = Array.from(fileList)
      const newFiles: AttachmentFile[] = []

      for (const file of fileArray) {
        const error = validateFile(file)
        if (error) {
          setDragError(error)
          continue
        }

        const preview = await createPreview(file)
        const attachmentFile: AttachmentFile = {
          id: `${Date.now()}-${Math.random()}`,
          file,
          preview,
          status: 'pending',
        }
        newFiles.push(attachmentFile)
      }

      if (newFiles.length > 0) {
        setFiles((prev) => [...prev, ...newFiles])
        setDragError(null)

        // Auto-upload if voucherId is available
        if (voucherId) {
          newFiles.forEach((attachmentFile) => uploadFile(attachmentFile))
        }
      }
    },
    [validateFile, createPreview, voucherId],
  )

  const uploadFile = useCallback(
    async (attachmentFile: AttachmentFile) => {
      if (!voucherId) {
        attachmentFile.status = 'error'
        attachmentFile.error = 'Voucher ID is required'
        setFiles((prev) => prev.map((f) => (f.id === attachmentFile.id ? attachmentFile : f)))
        onUploadError?.(attachmentFile, attachmentFile.error)
        return
      }

      attachmentFile.status = 'uploading'
      setFiles((prev) => prev.map((f) => (f.id === attachmentFile.id ? attachmentFile : f)))

      try {
        const voucherService = await import('@/services/voucher')
        await voucherService.uploadVoucherAttachment(voucherId, attachmentFile.file)

        attachmentFile.status = 'success'
        setFiles((prev) => prev.map((f) => (f.id === attachmentFile.id ? attachmentFile : f)))
        onUploadSuccess?.(attachmentFile)
      } catch (error: any) {
        const errorMessage =
          error?.error?.message || error?.message || 'Upload failed. Please try again.'
        attachmentFile.status = 'error'
        attachmentFile.error = errorMessage
        setFiles((prev) => prev.map((f) => (f.id === attachmentFile.id ? attachmentFile : f)))
        onUploadError?.(attachmentFile, errorMessage)
      }
    },
    [voucherId, onUploadSuccess, onUploadError],
  )

  const handleDragEnter = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      e.stopPropagation()
      if (!disabled) {
        setIsDragging(true)
        setDragError(null)
      }
    },
    [disabled],
  )

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragging(false)
  }, [])

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
  }, [])

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      e.stopPropagation()
      setIsDragging(false)

      if (disabled) return

      const droppedFiles = e.dataTransfer.files
      if (droppedFiles.length > 0) {
        handleFiles(droppedFiles)
      }
    },
    [disabled, handleFiles],
  )

  const handleFileInput = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const selectedFiles = e.target.files
      if (selectedFiles && selectedFiles.length > 0) {
        handleFiles(selectedFiles)
      }
      // Reset input to allow selecting the same file again
      e.target.value = ''
    },
    [handleFiles],
  )

  const removeFile = useCallback((id: string) => {
    setFiles((prev) => prev.filter((f) => f.id !== id))
  }, [])

  const retryUpload = useCallback(
    (attachmentFile: AttachmentFile) => {
      uploadFile(attachmentFile)
    },
    [uploadFile],
  )

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  const getFileIcon = (file: File) => {
    if (file.type.startsWith('image/')) {
      return <ImageIcon className="size-4" />
    }
    return <FileText className="size-4" />
  }

  return (
    <div className="space-y-4">
      {/* Dropzone */}
      <Card
        className={cn(
          'border-2 border-dashed transition-colors',
          isDragging && !disabled
            ? 'border-primary bg-primary/5'
            : disabled
              ? 'border-muted bg-muted/50 cursor-not-allowed'
              : 'border-muted hover:border-primary/50 cursor-pointer',
        )}
        onDragEnter={handleDragEnter}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
      >
        <CardContent className="p-6">
          <div className="flex flex-col items-center justify-center gap-4 text-center">
            <div
              className={cn(
                'rounded-full p-3',
                disabled ? 'bg-muted' : 'bg-primary/10 text-primary',
              )}
            >
              <Upload className="size-6" />
            </div>
            <div className="space-y-1">
              <p className="text-sm font-medium">
                {disabled ? 'Attachments disabled' : 'Drag and drop files here'}
              </p>
              <p className="text-xs text-muted-foreground">
                {acceptedTypes.join(', ')} (max {formatFileSize(maxSize)})
              </p>
            </div>
            {!disabled && (
              <label htmlFor="file-upload">
                <Button type="button" variant="outline" size="sm" asChild>
                  <span>Select Files</span>
                </Button>
                <input
                  id="file-upload"
                  type="file"
                  className="hidden"
                  multiple
                  accept={acceptedTypes.join(',')}
                  onChange={handleFileInput}
                  disabled={disabled}
                />
              </label>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Error Alert */}
      {dragError && (
        <Alert variant="destructive">
          <AlertCircle className="size-4" />
          <AlertDescription>{dragError}</AlertDescription>
        </Alert>
      )}

      {/* File List */}
      {files.length > 0 && (
        <div className="space-y-2">
          {files.map((attachmentFile) => (
            <Card key={attachmentFile.id} className="p-3">
              <div className="flex items-start gap-3">
                {/* Preview or Icon */}
                <div className="flex-shrink-0">
                  {attachmentFile.preview ? (
                    <img
                      src={attachmentFile.preview}
                      alt={attachmentFile.file.name}
                      className="size-12 rounded object-cover"
                    />
                  ) : (
                    <div className="flex size-12 items-center justify-center rounded bg-muted">
                      {getFileIcon(attachmentFile.file)}
                    </div>
                  )}
                </div>

                {/* File Info */}
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">{attachmentFile.file.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {formatFileSize(attachmentFile.file.size)}
                  </p>
                  {attachmentFile.error && (
                    <p className="text-xs text-destructive mt-1">{attachmentFile.error}</p>
                  )}
                </div>

                {/* Status Badge */}
                <div className="flex items-center gap-2">
                  {attachmentFile.status === 'uploading' && (
                    <Loader2 className="size-4 animate-spin text-muted-foreground" />
                  )}
                  {attachmentFile.status === 'success' && (
                    <Badge variant="outline" className="text-xs">
                      Uploaded
                    </Badge>
                  )}
                  {attachmentFile.status === 'error' && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => retryUpload(attachmentFile)}
                      className="text-xs h-auto py-1"
                    >
                      Retry
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="size-6"
                    onClick={() => removeFile(attachmentFile.id)}
                  >
                    <X className="size-3" />
                  </Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
