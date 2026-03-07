import path from "node:path";

import { ensureDir, writeText } from "./files.js";

function csvEscape(value) {
  const text = String(value ?? "");
  if (/[",\n]/.test(text)) {
    return `"${text.replaceAll('"', '""')}"`;
  }
  return text;
}

export async function exportSentencesCsv(projectPaths, project, structuredBook) {
  const rows = [
    [
      "sentence_id",
      "chapter_id",
      "chapter_title",
      "paragraph_id",
      "sentence_index_in_book",
      "sentence_index_in_paragraph",
      "sentence_text",
      "book_title",
      "book_author",
      "suggested_audio_file",
      "tts_status",
      "speaker",
      "notes",
    ].join(","),
  ];

  for (const chapter of structuredBook.chapters) {
    for (const paragraph of chapter.paragraphs) {
      for (const sentence of paragraph.sentences ?? []) {
        rows.push(
          [
            sentence.id,
            chapter.id,
            chapter.title,
            paragraph.id,
            sentence.indexInBook,
            sentence.indexInParagraph,
            sentence.content,
            structuredBook.book.title,
            structuredBook.book.author,
            `${sentence.id}_${sentence.content.slice(0, 24)}.wav`,
            "pending",
            "",
            "",
          ]
            .map(csvEscape)
            .join(","),
        );
      }
    }
  }

  const fileName = `sentences-${Date.now()}.csv`;
  const targetPath = path.join(projectPaths.exportsDir, fileName);
  await ensureDir(projectPaths.exportsDir);
  await writeText(targetPath, `\uFEFF${rows.join("\n")}\n`);

  project.exports = {
    ...(project.exports ?? {}),
    latestSentencesCsv: path.relative(projectPaths.projectDir, targetPath),
  };

  return {
    fileName,
    relativePath: project.exports.latestSentencesCsv,
    sentenceCount: rows.length - 1,
  };
}
