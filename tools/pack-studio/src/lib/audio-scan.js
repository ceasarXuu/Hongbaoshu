import fs from "node:fs/promises";
import path from "node:path";

import { ensureDir } from "./files.js";

const AUDIO_EXTENSIONS = new Set([".mp3", ".wav", ".ogg"]);

function sentenceIds(structuredBook) {
  return structuredBook.chapters.flatMap((chapter) =>
    chapter.paragraphs.flatMap((paragraph) => (paragraph.sentences ?? []).map((sentence) => sentence.id)),
  );
}

export async function importNarrationDirectory(projectPaths, structuredBook, sourceDir) {
  const stat = await fs.stat(sourceDir).catch(() => null);
  if (!stat?.isDirectory()) {
    throw new Error("Narration directory does not exist.");
  }

  const targetDir = path.join(projectPaths.assetsDir, "audio", "narration");
  await fs.rm(targetDir, { recursive: true, force: true });
  await ensureDir(targetDir);

  const entries = await fs.readdir(sourceDir, { withFileTypes: true });
  const expected = new Set(sentenceIds(structuredBook));
  const matchedIds = new Set();
  const orphanFiles = [];
  const duplicatePrefixes = [];

  for (const entry of entries) {
    if (!entry.isFile()) {
      continue;
    }
    const ext = path.extname(entry.name).toLowerCase();
    if (!AUDIO_EXTENSIONS.has(ext)) {
      continue;
    }

    const sourcePath = path.join(sourceDir, entry.name);
    const targetPath = path.join(targetDir, entry.name);
    await fs.copyFile(sourcePath, targetPath);

    const prefix = entry.name.split("_")[0];
    if (!expected.has(prefix)) {
      orphanFiles.push(entry.name);
      continue;
    }
    if (matchedIds.has(prefix)) {
      duplicatePrefixes.push(prefix);
      continue;
    }
    matchedIds.add(prefix);
  }

  const expectedIds = [...expected];
  const missingSentenceIds = expectedIds.filter((id) => !matchedIds.has(id));

  return {
    importedDirectory: sourceDir,
    matchedCount: matchedIds.size,
    missingCount: missingSentenceIds.length,
    orphanCount: orphanFiles.length,
    duplicatePrefixCount: duplicatePrefixes.length,
    coverage: expectedIds.length === 0 ? 0 : Number((matchedIds.size / expectedIds.length).toFixed(4)),
    missingSentenceIds,
    orphanFiles,
    duplicatePrefixes,
  };
}
