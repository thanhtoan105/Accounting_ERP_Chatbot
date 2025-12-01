import { useMemo } from 'react';

interface FormattedMessageProps {
    content: string;
    className?: string;
}

/**
 * Renders chat message content with basic markdown formatting.
 * Supports: **bold**, *italic*, `code`, - list items, numbered lists
 */
export function FormattedMessage({ content, className }: FormattedMessageProps) {
    const formattedContent = useMemo(() => {
        return formatMarkdown(content);
    }, [content]);

    return (
        <div
            className={className}
            dangerouslySetInnerHTML={{ __html: formattedContent }}
        />
    );
}

/**
 * Simple markdown formatter for chat messages.
 * Converts basic markdown syntax to HTML.
 */
function formatMarkdown(text: string): string {
    let html = text;

    // Escape HTML to prevent XSS
    html = html
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');

    // Bold: **text** or __text__
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/__(.+?)__/g, '<strong>$1</strong>');

    // Italic: *text* or _text_ (but not inside words)
    html = html.replace(/(?<!\w)\*([^*]+?)\*(?!\w)/g, '<em>$1</em>');
    html = html.replace(/(?<!\w)_([^_]+?)_(?!\w)/g, '<em>$1</em>');

    // Inline code: `code`
    html = html.replace(/`([^`]+?)`/g, '<code class="bg-gray-200 px-1 rounded text-sm">$1</code>');

    // Line breaks
    html = html.replace(/\n/g, '<br />');

    // List items: - item or * item (at start of line after br)
    html = html.replace(/(?:^|<br \/>)\s*[-*]\s+(.+?)(?=<br \/>|$)/g, '<br /><span class="flex gap-2"><span>•</span><span>$1</span></span>');

    // Numbered list: 1. item, 2. item
    html = html.replace(/(?:^|<br \/>)\s*(\d+)\.\s+(.+?)(?=<br \/>|$)/g, '<br /><span class="flex gap-2"><span>$1.</span><span>$2</span></span>');

    // Clean up leading br
    html = html.replace(/^<br \/>/, '');

    return html;
}
