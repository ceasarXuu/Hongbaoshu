const PACK_ID_PATTERN = /^[A-Za-z0-9._-]{3,120}$/;

export function validateProject(project, structuredBook) {
  const items = [];
  const push = (level, code, message) => items.push({ level, code, message });

  if (!project.metadata.packId || !PACK_ID_PATTERN.test(project.metadata.packId)) {
    push("error", "PACK_ID_INVALID", "packId is missing or contains invalid characters.");
  }

  if (!Number.isInteger(project.metadata.packVersion) || project.metadata.packVersion <= 0) {
    push("error", "PACK_VERSION_INVALID", "packVersion must be a positive integer.");
  }

  if (!Number.isInteger(project.metadata.formatVersion) || project.metadata.formatVersion <= 0) {
    push("error", "FORMAT_VERSION_INVALID", "formatVersion must be a positive integer.");
  }

  if (!structuredBook?.book?.title) {
    push("error", "BOOK_TITLE_MISSING", "book.title is required.");
  }

  if (!structuredBook?.chapters?.length) {
    push("error", "CHAPTERS_MISSING", "No chapters were generated.");
  }

  const chapterIds = new Set();
  const paragraphIds = new Set();
  const sentenceIds = new Set();
  let sentenceCount = 0;

  for (const chapter of structuredBook?.chapters ?? []) {
    if (chapterIds.has(chapter.id)) {
      push("error", "DUPLICATE_CHAPTER_ID", `Duplicate chapter.id: ${chapter.id}`);
    }
    chapterIds.add(chapter.id);

    for (const paragraph of chapter.paragraphs ?? []) {
      if (paragraphIds.has(paragraph.id)) {
        push("error", "DUPLICATE_PARAGRAPH_ID", `Duplicate paragraph.id: ${paragraph.id}`);
      }
      paragraphIds.add(paragraph.id);

      for (const sentence of paragraph.sentences ?? []) {
        sentenceCount += 1;
        if (sentenceIds.has(sentence.id)) {
          push("error", "DUPLICATE_SENTENCE_ID", `Duplicate sentence.id: ${sentence.id}`);
        }
        sentenceIds.add(sentence.id);
      }
    }
  }

  if (sentenceCount === 0) {
    push("warning", "NO_SENTENCES", "No sentences were generated from the input text.");
  }

  return {
    status: items.some((item) => item.level === "error") ? "failed" : "passed",
    summary: {
      errors: items.filter((item) => item.level === "error").length,
      warnings: items.filter((item) => item.level === "warning").length,
      infos: items.filter((item) => item.level === "info").length,
      chapters: chapterIds.size,
      paragraphs: paragraphIds.size,
      sentences: sentenceCount,
    },
    items,
  };
}
