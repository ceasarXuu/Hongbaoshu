function sentenceChunks(text) {
  const matches = text.match(/[^。！？；!?;\r\n]+[。！？；!?;]?/g) ?? [];
  const chunks = matches.map((value) => value.trim()).filter(Boolean);
  return chunks.length > 0 ? chunks : [text.trim()].filter(Boolean);
}

function chapterHeading(line) {
  const trimmed = line.trim();
  if (!trimmed) {
    return null;
  }
  if (trimmed.startsWith("# ")) {
    return trimmed.slice(2).trim();
  }
  if (/^第.+[章节回卷篇部]/.test(trimmed)) {
    return trimmed;
  }
  return null;
}

function createChapter(chapterIndex, title) {
  return {
    id: String(chapterIndex).padStart(2, "0"),
    title,
    paragraphs: [],
  };
}

function flushParagraph(chapter, chapterId, paragraphIndexRef, lines, sentenceIndexRef) {
  const text = lines.join("\n").trim();
  lines.length = 0;
  if (!text) {
    return;
  }

  paragraphIndexRef.current += 1;
  const paragraphId = `${chapterId}-${String(paragraphIndexRef.current).padStart(3, "0")}`;
  const sentences = sentenceChunks(text).map((content, sentenceOffset) => {
    sentenceIndexRef.current += 1;
    return {
      id: `${paragraphId}-s${String(sentenceOffset + 1).padStart(3, "0")}`,
      content,
      chapterId,
      paragraphId,
      indexInParagraph: sentenceOffset + 1,
      indexInBook: sentenceIndexRef.current,
    };
  });

  chapter.paragraphs.push({
    id: paragraphId,
    type: "text",
    content: text,
    sentences,
  });
}

export function normalizeTxt(text, metadata) {
  const lines = text.replace(/\r\n/g, "\n").split("\n");
  const chapters = [];
  const sentenceIndexRef = { current: 0 };
  let chapterIndex = 1;
  let chapter = null;
  let paragraphIndexRef = { current: 0 };
  const paragraphLines = [];

  const ensureChapter = () => {
    if (!chapter) {
      chapter = createChapter(chapterIndex, metadata.title || "正文");
    }
    return chapter;
  };

  for (const line of lines) {
    const heading = chapterHeading(line);
    if (heading) {
      if (chapter) {
        flushParagraph(chapter, chapter.id, paragraphIndexRef, paragraphLines, sentenceIndexRef);
        chapters.push(chapter);
      }
      chapter = createChapter(chapterIndex, heading);
      paragraphIndexRef = { current: 0 };
      chapterIndex += 1;
      continue;
    }

    if (!line.trim()) {
      const currentChapter = ensureChapter();
      flushParagraph(currentChapter, currentChapter.id, paragraphIndexRef, paragraphLines, sentenceIndexRef);
      continue;
    }

    ensureChapter();
    paragraphLines.push(line.trim());
  }

  const currentChapter = ensureChapter();
  flushParagraph(currentChapter, currentChapter.id, paragraphIndexRef, paragraphLines, sentenceIndexRef);
  if (currentChapter.paragraphs.length > 0 || chapters.length === 0) {
    chapters.push(currentChapter);
  }

  return {
    book: {
      title: metadata.title?.trim() || "未命名书籍",
      author: metadata.author?.trim() || "未知作者",
      edition: metadata.edition?.trim() || "1.0",
    },
    chapters,
  };
}
