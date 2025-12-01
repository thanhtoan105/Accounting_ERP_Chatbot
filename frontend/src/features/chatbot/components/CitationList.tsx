import { ExternalLink, FileText } from 'lucide-react'
import type { Citation } from '../types/chatbot'
import { Card } from '@/components/ui/card'

interface CitationListProps {
  citations: Citation[]
}

export function CitationList({ citations }: CitationListProps) {
  if (citations.length === 0) return null

  // Open voucher in new tab to preserve chat state
  const handleCitationClick = (link: string) => {
    window.open(link, '_blank', 'noopener,noreferrer')
  }

  return (
    <div className="space-y-2">
      <div className="text-xs font-semibold text-gray-700 mb-2">
        Nguồn tham khảo ({citations.length})
      </div>

      <div className="space-y-2">
        {citations.map((citation) => (
          <Card
            key={citation.entityId}
            className="p-3 hover:bg-gray-50 transition-all cursor-pointer chatbot-citation"
            onClick={() => handleCitationClick(citation.link)}
          >
            <div className="flex items-start gap-2 group">
              {/* Icon */}
              <FileText className="w-4 h-4 text-blue-600 flex-shrink-0 mt-0.5" />

              {/* Content */}
              <div className="flex-1 min-w-0">
                {/* Voucher Number */}
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-blue-600 group-hover:underline">
                    {citation.voucherNumber}
                  </span>
                  <ExternalLink className="w-3 h-3 text-gray-400" />
                </div>

                {/* Excerpt */}
                <p className="text-xs text-gray-600 mt-1 line-clamp-2">{citation.excerpt}</p>

                {/* Relevance Score */}
                {citation.relevanceScore > 0 && (
                  <div className="text-xs text-gray-400 mt-1">
                    Độ liên quan: {Math.round(citation.relevanceScore * 100)}%
                  </div>
                )}
              </div>
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}
