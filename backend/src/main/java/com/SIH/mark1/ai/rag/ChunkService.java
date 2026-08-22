package com.SIH.mark1.ai.rag;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Splits markdown knowledge documents into retrievable chunks.
 *
 * <p>The previous implementation split on blank lines and nothing else. That loses the two
 * things retrieval depends on most:</p>
 *
 * <ol>
 *   <li><strong>Headings were discarded.</strong> In {@code Water Supply.md} the heading
 *       "## SLA" carries the topic while the paragraph underneath only says "Resolution within
 *       72 hours." Indexed alone, that paragraph contains neither "SLA" nor "water", so the
 *       query "water supply SLA kitne din?" could not match it. Each chunk now carries a
 *       breadcrumb of its enclosing headings, so the topic travels with the text.</li>
 *   <li><strong>No size bounds.</strong> A long section became one oversized chunk that
 *       diluted its own embedding (many topics averaged into one vector), while a one-line
 *       paragraph became a chunk too small to be meaningful. Chunks are now bounded, with
 *       overlap so a fact split across a boundary survives in both halves.</li>
 * </ol>
 *
 * <p>Tables and list blocks are kept with their heading rather than exploded row-by-row,
 * since an isolated table row is rarely interpretable on its own.</p>
 */
@Service
public class ChunkService {

    /** Approximate character budget per chunk (~500 tokens for Latin text). */
    private static final int TARGET_CHARS = 1_800;

    /** Hard ceiling; beyond this we split even mid-section. */
    private static final int MAX_CHARS = 2_400;

    /** Chunks shorter than this are merged forward — too small to embed usefully. */
    private static final int MIN_CHARS = 120;

    /** Trailing characters repeated into the next chunk to preserve cross-boundary context. */
    private static final int OVERLAP_CHARS = 220;

    private static final int MAX_HEADING_DEPTH = 6;

    /**
     * Splits content into breadcrumb-prefixed chunks.
     * Signature is unchanged so existing callers keep working.
     */
    public List<String> chunk(String content) {
        List<Chunk> detailed = chunkWithMetadata(content);
        List<String> plain = new ArrayList<>(detailed.size());
        for (Chunk chunk : detailed) {
            plain.add(chunk.text());
        }
        return plain;
    }

    /**
     * Splits content and retains the heading trail for each chunk, so callers can store it
     * as Qdrant payload metadata (useful for citations and for filtered search).
     *
     * @param content raw markdown
     * @return ordered chunks, each already prefixed with its breadcrumb
     */
    public List<Chunk> chunkWithMetadata(String content) {
        List<Chunk> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }

        Deque<String> headingStack = new ArrayDeque<>();
        StringBuilder buffer = new StringBuilder();
        String bufferBreadcrumb = "";

        for (String block : content.split("\\R\\s*\\R")) {
            String cleaned = block.strip();
            if (cleaned.isBlank()) {
                continue;
            }

            int level = headingLevel(cleaned);
            if (level > 0) {
                // A heading starts a new section: flush what we accumulated under the old one.
                flush(chunks, buffer, bufferBreadcrumb);
                updateHeadingStack(headingStack, level, stripHeadingMarkers(cleaned));
                bufferBreadcrumb = String.join(" > ", headingStack);
                continue;
            }

            String normalized = normalizeBlock(cleaned);
            if (normalized.isBlank()) {
                continue;
            }

            if (bufferBreadcrumb.isEmpty() && buffer.isEmpty()) {
                bufferBreadcrumb = String.join(" > ", headingStack);
            }

            // Emit before exceeding the target so chunks stay topically tight.
            if (buffer.length() + normalized.length() + 1 > TARGET_CHARS && buffer.length() >= MIN_CHARS) {
                String carry = tailOverlap(buffer.toString());
                flush(chunks, buffer, bufferBreadcrumb);
                buffer.append(carry);
            }

            if (!buffer.isEmpty()) {
                buffer.append('\n');
            }
            buffer.append(normalized);

            // A single block can exceed the ceiling on its own; hard-split it.
            while (buffer.length() > MAX_CHARS) {
                int cut = findSplitPoint(buffer, MAX_CHARS);
                String head = buffer.substring(0, cut).strip();
                if (!head.isBlank()) {
                    chunks.add(build(bufferBreadcrumb, head));
                }
                String remainder = buffer.substring(cut).strip();
                buffer.setLength(0);
                buffer.append(remainder);
            }
        }

        flush(chunks, buffer, bufferBreadcrumb);
        return chunks;
    }

    /** A chunk plus the heading trail it came from. */
    public record Chunk(String breadcrumb, String body, String text) {
    }

    // ──────────────────────────────────────────
    // Internals
    // ──────────────────────────────────────────

    private void flush(List<Chunk> chunks, StringBuilder buffer, String breadcrumb) {
        String body = buffer.toString().strip();
        buffer.setLength(0);
        if (body.isBlank()) {
            return;
        }
        // Merge a runt into the previous chunk instead of indexing a fragment.
        if (body.length() < MIN_CHARS && !chunks.isEmpty()) {
            Chunk previous = chunks.remove(chunks.size() - 1);
            String merged = previous.body() + "\n" + body;
            chunks.add(build(previous.breadcrumb(), merged));
            return;
        }
        chunks.add(build(breadcrumb, body));
    }

    /**
     * Prefixes the breadcrumb so the embedding sees the topic alongside the detail.
     * This is what lets "SLA kitne din?" reach a paragraph that only says "72 hours".
     */
    private Chunk build(String breadcrumb, String body) {
        String text = breadcrumb == null || breadcrumb.isBlank()
                ? body
                : breadcrumb + "\n" + body;
        return new Chunk(breadcrumb == null ? "" : breadcrumb, body, text);
    }

    private int headingLevel(String block) {
        // Only treat as a heading if it is a single line, so fenced code is not misread.
        if (block.contains("\n") || !block.startsWith("#")) {
            return 0;
        }
        int level = 0;
        while (level < block.length() && block.charAt(level) == '#') {
            level++;
        }
        if (level > MAX_HEADING_DEPTH || level >= block.length()) {
            return 0;
        }
        return Character.isWhitespace(block.charAt(level)) ? level : 0;
    }

    private String stripHeadingMarkers(String block) {
        return block.replaceFirst("^#+\\s*", "").strip();
    }

    /** Keeps the stack consistent with markdown nesting (an h2 replaces a deeper trail). */
    private void updateHeadingStack(Deque<String> stack, int level, String heading) {
        while (stack.size() >= level) {
            stack.pollLast();
        }
        while (stack.size() < level - 1) {
            stack.offerLast("");
        }
        stack.offerLast(heading);
    }

    /** Collapses intra-line whitespace but preserves line structure for tables and lists. */
    private String normalizeBlock(String block) {
        String[] lines = block.split("\\R");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.replaceAll("[ \\t]+", " ").strip();
            if (trimmed.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(trimmed);
        }
        return builder.toString();
    }

    private String tailOverlap(String text) {
        if (text.length() <= OVERLAP_CHARS) {
            return text;
        }
        String tail = text.substring(text.length() - OVERLAP_CHARS);
        int boundary = tail.indexOf(' ');
        return boundary > 0 ? tail.substring(boundary + 1) : tail;
    }

    /** Prefers a sentence boundary, then a space, before cutting mid-word. */
    private int findSplitPoint(CharSequence text, int limit) {
        for (int i = limit - 1; i > limit / 2; i--) {
            char ch = text.charAt(i);
            if (ch == '.' || ch == '?' || ch == '!' || ch == '\n') {
                return i + 1;
            }
        }
        for (int i = limit - 1; i > limit / 2; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i + 1;
            }
        }
        return limit;
    }
}
