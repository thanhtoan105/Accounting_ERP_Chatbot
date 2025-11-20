import { useCallback, useState } from 'react'
import { Upload, X, FileText, Image as ImageIcon, AlertCircle, Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Document, Page, pdfjs } from 'react-pdf'
import 'react-pdf/dist/Page/AnnotationLayer.css'
import 'react-pdf/dist/Page/TextLayer.css'
import { uploadPurchaseBillAttachment } from '@/services/purchaseBill'
import type { PurchaseBillAttachmentDTO } from '@/types/attachment'

// Configure PDF.js worker
pdfjs.GlobalWorkerOptions.workerSrc = `//cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjs.version}/pdf.worker.min.js`

export interface AttachmentFile {
  id: string
  file: File
  preview?: string
  previewType?: 'image' | 'pdf'
  status: 'pending' | 'uploading' | 'success' | 'error'
  error?: string
  progress?: number
}

interface PurchaseBillAttachmentDropzoneProps {
  billId: string | null
  onUploadSuccess?: (file: AttachmentFile) => void
  onUploadError?: (file: AttachmentFile, error: string) => void
  disabled?: boolean
  maxSize?: number // in bytes, default 20MB
  maxFiles?: number // default 10
  acceptedTypes?: string[] // MIME types, default ['image/*', 'application/pdf']
  existingCount?: number // Current number of attachments
}

const DEFAULT_MAX_SIZE = 20 * 1024 * 1024 // 20MB
const DEFAULT_MAX_FILES = 10
const DEFAULT_ACCEPTED_TYPES = ['image/*', 'application/pdf']

export function PurchaseBillAttachmentDropzone({
  billId,
  onUploadSuccess,
  onUploadError,
  disabled = false,
  maxSize = DEFAULT_MAX_SIZE,
  maxFiles = DEFAULT_MAX_FILES,
  acceptedTypes = DEFAULT_ACCEPTED_TYPES,
  existingCount = 0,
}: PurchaseBillAttachmentDropzoneProps) {
  const [files, setFiles] = useState<AttachmentFile[]>([])
  const [isDragging, setIsDragging] = useState(false)
  const [dragError, setDragError] = useState<string | null>(null)

  const validateFile = useCallback(
    (file: File, currentFileCount: number): string | null => {
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

      // Check total file count
      if (currentFileCount + existingCount >= maxFiles) {
        return `Maximum ${maxFiles} files allowed. You have ${existingCount} existing files.`
      }

      return null
    },
    [acceptedTypes, maxSize, maxFiles, existingCount],
  )

  const createPreview = useCallback(
    (file: File): Promise<{ preview?: string; previewType?: 'image' | 'pdf' }> => {
      return new Promise((resolve) => {
        if (file.type.startsWith('image/')) {
          const reader = new FileReader()
          reader.onload = (e) =>
            resolve({ preview: e.target?.result as string, previewType: 'image' })
          reader.onerror = () => resolve({})
          reader.readAsDataURL(file)
        } else if (file.type === 'application/pdf') {
          const reader = new FileReader()
          reader.onload = (e) =>
            resolve({ preview: e.target?.result as string, previewType: 'pdf' })
          reader.onerror = () => resolve({})
          reader.readAsDataURL(file)
        } else {
          resolve({})
        }
      })
    },
    [],
  )

  const handleFiles = useCallback(
    async (fileList: FileList | File[]) => {
      const fileArray = Array.from(fileList)
      const newFiles: AttachmentFile[] = []
      let currentCount = files.length

      for (const file of fileArray) {
        const error = validateFile(file, currentCount)
        if (error) {
          setDragError(error)
          continue
        }

        const previewResult = await createPreview(file)
        const attachmentFile: AttachmentFile = {
          id: `${Date.now()}-${Math.random()}`,
          file,
          preview: previewResult.preview,
          previewType: previewResult.previewType,
          status: 'pending',
        }
        newFiles.push(attachmentFile)
        currentCount++
      }

      if (newFiles.length > 0) {
        setFiles((prev) => [...prev, ...newFiles])
        setDragError(null)

        // Auto-upload if billId is available
        if (billId) {
          newFiles.forEach((attachmentFile) => uploadFile(attachmentFile))
        }
      }
    },
    [validateFile, createPreview, billId, files.length],
  )

  const uploadFile = useCallback(
    async (attachmentFile: AttachmentFile, retryCount = 0) => {
      if (!billId) {
        attachmentFile.status = 'error'
        attachmentFile.error = 'Bill ID is required'
        setFiles((prev) => prev.map((f) => (f.id === attachmentFile.id ? attachmentFile : f)))
        onUploadError?.(attachmentFile, attachmentFile.error)
        return
      }

      attachmentFile.status = 'uploading'
      attachmentFile.progress = 0
      setFiles((prev) => prev.map((f) => (f.id === attachmentFile.id ? attachmentFile : f)))

      try {
        await uploadPurchaseBillAttachment(billId, attachmentFile.file, (progress) => {
          attachmentFile.progress = progress
          setFiles((prev) => prev.map((f) => (f.id === attachmentFile.id ? attachmentFile : f)))
        })

        attachmentFile.status = 'success'
        attachmentFile.progress = 100
        setFiles((prev) => prev.map((f) => (f.id === attachmentFile.id ? attachmentFile : f)))
        onUploadSuccess?.(attachmentFile)
      } catch (error: any) {
        const errorMessage =
          error?.error?.message || error?.message || 'Upload failed. Please try again.'

        // Retry logic: 3 retries with exponential backoff
        const maxRetries = 3
        if (retryCount < maxRetries) {
          const delay = Math.pow(2, retryCount) * 1000 // Exponential backoff: 1s, 2s, 4s
          setTimeout(() => {
            uploadFile(attachmentFile, retryCount + 1)
          }, delay)
        } else {
          attachmentFile.status = 'error'
          attachmentFile.error = errorMessage
          attachmentFile.progress = 0
          setFiles((prev) => prev.map((f) => (f.id === attachmentFile.id ? attachmentFile : f)))
          onUploadError?.(attachmentFile, errorMessage)
        }
      }
    },
    [billId, onUploadSuccess, onUploadError],
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
                {acceptedTypes.join(', ')} (max {formatFileSize(maxSize)}, {maxFiles} files)
              </p>
              {existingCount > 0 && (
                <p className="text-xs text-muted-foreground">
                  {existingCount} existing file{existingCount !== 1 ? 's' : ''}
                </p>
              )}
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
                  {attachmentFile.previewType === 'image' && attachmentFile.preview ? (
                    <img
                      src={attachmentFile.preview}
                      alt={attachmentFile.file.name}
                      className="size-12 rounded object-cover"
                    />
                  ) : attachmentFile.previewType === 'pdf' && attachmentFile.preview ? (
                    <div className="size-12 rounded overflow-hidden bg-muted border">
                      <Document
                        file={attachmentFile.preview}
                        loading={
                          <div className="flex items-center justify-center h-full text-xs">
                            Loading PDF...
                          </div>
                        }
                        error={
                          <div className="flex items-center justify-center h-full text-xs text-destructive">
                            PDF Error
                          </div>
                        }
                      >
                        <Page
                          pageNumber={1}
                          width={48}
                          renderTextLayer={false}
                          renderAnnotationLayer={false}
                        />
                      </Document>
                    </div>
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

                {/* Progress Bar */}
                {attachmentFile.status === 'uploading' && attachmentFile.progress !== undefined && (
                  <div className="w-full mt-2">
                    <div className="h-2 bg-muted rounded-full overflow-hidden">
                      <div
                        className="h-full bg-primary transition-all duration-300"
                        style={{ width: `${attachmentFile.progress}%` }}
                      />
                    </div>
                    <p className="text-xs text-muted-foreground mt-1">
                      {Math.round(attachmentFile.progress)}%
                    </p>
                  </div>
                )}

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
                  {attachmentFile.status !== 'uploading' && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="size-6"
                      onClick={() => removeFile(attachmentFile.id)}
                    >
                      <X className="size-3" />
                    </Button>
                  )}
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
