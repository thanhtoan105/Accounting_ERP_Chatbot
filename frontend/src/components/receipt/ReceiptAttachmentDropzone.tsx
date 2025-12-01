'use client'

import { useCallback, useState, useEffect } from 'react'
import {
  Upload,
  X,
  FileText,
  Image as ImageIcon,
  AlertCircle,
  Loader2,
  Download,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { toast } from 'sonner'
import {
  uploadReceiptAttachment,
  getReceiptAttachments,
  deleteReceiptAttachment,
  downloadReceiptAttachment,
  type ReceiptAttachmentDTO,
} from '@/services/receipt'

export interface AttachmentFile {
  id: string
  file?: File
  preview?: string
  previewType?: 'image' | 'pdf'
  status: 'pending' | 'uploading' | 'success' | 'error'
  error?: string
  progress?: number
  // For existing attachments
  existingAttachment?: ReceiptAttachmentDTO
}

interface ReceiptAttachmentDropzoneProps {
  receiptId: string | null
  disabled?: boolean
  maxFiles?: number // default 10
  maxTotalSize?: number // in bytes, default 20MB
  maxFileSize?: number // in bytes, default 10MB
  acceptedTypes?: string[] // MIME types
}

const DEFAULT_MAX_FILES = 10
const DEFAULT_MAX_TOTAL_SIZE = 20 * 1024 * 1024 // 20MB
const DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB per file
const DEFAULT_ACCEPTED_TYPES = ['image/*', 'application/pdf']

/**
 * Receipt attachment dropzone component.
 * AC6.2-08: Up to 10 files and ≤ 20 MB total per receipt.
 */
export function ReceiptAttachmentDropzone({
  receiptId,
  disabled = false,
  maxFiles = DEFAULT_MAX_FILES,
  maxTotalSize = DEFAULT_MAX_TOTAL_SIZE,
  maxFileSize = DEFAULT_MAX_FILE_SIZE,
  acceptedTypes = DEFAULT_ACCEPTED_TYPES,
}: ReceiptAttachmentDropzoneProps) {
  const [files, setFiles] = useState<AttachmentFile[]>([])
  const [existingAttachments, setExistingAttachments] = useState<ReceiptAttachmentDTO[]>([])
  const [isDragging, setIsDragging] = useState(false)
  const [dragError, setDragError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  // Load existing attachments when receiptId is provided
  useEffect(() => {
    if (!receiptId) return

    let mounted = true
    setLoading(true)

    getReceiptAttachments(receiptId)
      .then((attachments) => {
        if (mounted) {
          setExistingAttachments(attachments)
        }
      })
      .catch((err) => {
        console.error('Failed to load attachments:', err)
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })

    return () => {
      mounted = false
    }
  }, [receiptId])

  const totalExistingSize = (existingAttachments || []).reduce(
    (sum, att) => sum + (att.fileSize || 0),
    0,
  )
  const totalPendingSize = (files || []).reduce((sum, f) => sum + (f.file?.size || 0), 0)
  const totalSize = totalExistingSize + totalPendingSize
  const totalCount = (existingAttachments || []).length + (files || []).length

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
        return `Invalid file type. Only images and PDFs are allowed.`
      }

      // Check individual file size
      if (file.size > maxFileSize) {
        const maxSizeMB = (maxFileSize / (1024 * 1024)).toFixed(0)
        return `File size exceeds maximum allowed size of ${maxSizeMB}MB.`
      }

      // Check total count
      if (totalCount >= maxFiles) {
        return `Maximum ${maxFiles} files allowed per receipt.`
      }

      // Check total size
      if (totalSize + file.size > maxTotalSize) {
        const maxTotalMB = (maxTotalSize / (1024 * 1024)).toFixed(0)
        return `Total attachment size would exceed ${maxTotalMB}MB limit.`
      }

      return null
    },
    [acceptedTypes, maxFileSize, maxFiles, maxTotalSize, totalCount, totalSize],
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
          resolve({ previewType: 'pdf' })
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

      for (const file of fileArray) {
        const error = validateFile(file)
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
      }

      if (newFiles.length > 0) {
        setFiles((prev) => [...prev, ...newFiles])
        setDragError(null)

        // Auto-upload if receiptId is available
        if (receiptId) {
          newFiles.forEach((attachmentFile) => uploadFile(attachmentFile))
        }
      }
    },
    [validateFile, createPreview, receiptId],
  )

  const uploadFile = useCallback(
    async (attachmentFile: AttachmentFile) => {
      if (!receiptId || !attachmentFile.file) {
        attachmentFile.status = 'error'
        attachmentFile.error = 'Receipt must be saved before uploading attachments'
        setFiles((prev) => prev.map((f) => (f.id === attachmentFile.id ? attachmentFile : f)))
        return
      }

      attachmentFile.status = 'uploading'
      attachmentFile.progress = 0
      setFiles((prev) => prev.map((f) => (f.id === attachmentFile.id ? attachmentFile : f)))

      try {
        const uploaded = await uploadReceiptAttachment(
          receiptId,
          attachmentFile.file,
          (progress) => {
            attachmentFile.progress = progress
            setFiles((prev) =>
              prev.map((f) => (f.id === attachmentFile.id ? { ...attachmentFile } : f)),
            )
          },
        )

        // Move to existing attachments
        setExistingAttachments((prev) => [...prev, uploaded])
        setFiles((prev) => prev.filter((f) => f.id !== attachmentFile.id))
        toast.success(`${attachmentFile.file.name} uploaded successfully`)
      } catch (error: any) {
        const errorMessage = error?.message || 'Upload failed. Please try again.'
        attachmentFile.status = 'error'
        attachmentFile.error = errorMessage
        attachmentFile.progress = 0
        setFiles((prev) => prev.map((f) => (f.id === attachmentFile.id ? attachmentFile : f)))
        toast.error(`Failed to upload ${attachmentFile.file.name}`)
      }
    },
    [receiptId],
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
      e.target.value = ''
    },
    [handleFiles],
  )

  const removeFile = useCallback((id: string) => {
    setFiles((prev) => prev.filter((f) => f.id !== id))
  }, [])

  const removeExistingAttachment = useCallback(
    async (attachmentId: string) => {
      if (!receiptId) return

      try {
        await deleteReceiptAttachment(receiptId, attachmentId)
        setExistingAttachments((prev) => prev.filter((a) => a.id !== attachmentId))
        toast.success('Attachment deleted')
      } catch (error: any) {
        toast.error('Failed to delete attachment')
      }
    },
    [receiptId],
  )

  const handleDownload = useCallback(
    async (attachment: ReceiptAttachmentDTO) => {
      if (!receiptId) return

      try {
        const blob = await downloadReceiptAttachment(receiptId, attachment.id)
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = attachment.fileName
        document.body.appendChild(a)
        a.click()
        window.URL.revokeObjectURL(url)
        document.body.removeChild(a)
      } catch (error: any) {
        toast.error('Failed to download attachment')
      }
    },
    [receiptId],
  )

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  const getFileIcon = (mimeType: string) => {
    if (mimeType.startsWith('image/')) {
      return <ImageIcon className="size-4" />
    }
    return <FileText className="size-4" />
  }

  return (
    <div className="space-y-4">
      {/* Summary */}
      <div className="flex items-center justify-between text-sm text-muted-foreground">
        <span>
          {(existingAttachments || []).length} of {maxFiles} files (
          {formatFileSize(totalExistingSize)} of {formatFileSize(maxTotalSize)})
        </span>
      </div>

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
                Images or PDFs (max {formatFileSize(maxFileSize)} per file, {maxFiles} files total)
              </p>
            </div>
            {!disabled && receiptId && (
              <label htmlFor="receipt-file-upload">
                <Button type="button" variant="outline" size="sm" asChild>
                  <span>Select Files</span>
                </Button>
                <input
                  id="receipt-file-upload"
                  type="file"
                  className="hidden"
                  multiple
                  accept={acceptedTypes.join(',')}
                  onChange={handleFileInput}
                  disabled={disabled}
                />
              </label>
            )}
            {!receiptId && (
              <p className="text-xs text-orange-600">
                Save the receipt first to enable attachments
              </p>
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

      {/* Loading */}
      {loading && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="size-4 animate-spin" />
          Loading attachments...
        </div>
      )}

      {/* Existing Attachments */}
      {(existingAttachments || []).length > 0 && (
        <div className="space-y-2">
          <p className="text-sm font-medium">Uploaded Files</p>
          {(existingAttachments || []).map((attachment) => (
            <Card key={attachment.id} className="p-3">
              <div className="flex items-center gap-3">
                <div className="flex size-10 items-center justify-center rounded bg-muted">
                  {getFileIcon(attachment.mimeType)}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">{attachment.fileName}</p>
                  <p className="text-xs text-muted-foreground">
                    {formatFileSize(attachment.fileSize)}
                  </p>
                </div>
                <div className="flex items-center gap-1">
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="size-8"
                    onClick={() => handleDownload(attachment)}
                  >
                    <Download className="size-4" />
                  </Button>
                  {!disabled && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="size-8"
                      onClick={() => removeExistingAttachment(attachment.id)}
                    >
                      <X className="size-4" />
                    </Button>
                  )}
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Pending Uploads */}
      {files.length > 0 && (
        <div className="space-y-2">
          <p className="text-sm font-medium">Uploading</p>
          {files.map((attachmentFile) => (
            <Card key={attachmentFile.id} className="p-3">
              <div className="flex items-start gap-3">
                {/* Preview or Icon */}
                <div className="flex-shrink-0">
                  {attachmentFile.previewType === 'image' && attachmentFile.preview ? (
                    <img
                      src={attachmentFile.preview}
                      alt={attachmentFile.file?.name}
                      className="size-10 rounded object-cover"
                    />
                  ) : (
                    <div className="flex size-10 items-center justify-center rounded bg-muted">
                      {attachmentFile.file && getFileIcon(attachmentFile.file.type)}
                    </div>
                  )}
                </div>

                {/* File Info */}
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium truncate">{attachmentFile.file?.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {attachmentFile.file && formatFileSize(attachmentFile.file.size)}
                  </p>
                  {attachmentFile.error && (
                    <p className="text-xs text-destructive mt-1">{attachmentFile.error}</p>
                  )}
                  {/* Progress Bar */}
                  {attachmentFile.status === 'uploading' &&
                    attachmentFile.progress !== undefined && (
                      <div className="mt-2">
                        <div className="h-1.5 bg-muted rounded-full overflow-hidden">
                          <div
                            className="h-full bg-primary transition-all duration-300"
                            style={{ width: `${attachmentFile.progress}%` }}
                          />
                        </div>
                      </div>
                    )}
                </div>

                {/* Status */}
                <div className="flex items-center gap-2">
                  {attachmentFile.status === 'uploading' && (
                    <Loader2 className="size-4 animate-spin text-muted-foreground" />
                  )}
                  {attachmentFile.status === 'error' && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => uploadFile(attachmentFile)}
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
