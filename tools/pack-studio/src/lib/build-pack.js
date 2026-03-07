import crypto from "node:crypto";
import fs from "node:fs";
import fsp from "node:fs/promises";
import path from "node:path";

import yazl from "yazl";

import { copyDirRecursive, ensureDir, exists, writeJson } from "./files.js";

async function sha256ForFile(filePath) {
  const content = await fsp.readFile(filePath);
  return crypto.createHash("sha256").update(content).digest("hex");
}

function addDirToZip(zipFile, rootDir, currentDir = rootDir) {
  const entries = fs.readdirSync(currentDir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(currentDir, entry.name);
    const zipPath = path.relative(rootDir, fullPath).replaceAll("\\", "/");
    if (entry.isDirectory()) {
      addDirToZip(zipFile, rootDir, fullPath);
    } else {
      zipFile.addFile(fullPath, zipPath);
    }
  }
}

export async function buildPack(projectPaths, project, structuredBook) {
  const buildId = `build-${Date.now()}`;
  const buildRoot = path.join(projectPaths.buildDir, buildId, "root");
  const textDir = path.join(buildRoot, "text");
  const distDir = path.join(projectPaths.buildDir, "dist");
  const bookJsonPath = path.join(textDir, "book.json");

  await ensureDir(textDir);
  await ensureDir(distDir);
  await writeJson(bookJsonPath, {
    book: structuredBook.book,
    chapters: structuredBook.chapters.map((chapter) => ({
      id: chapter.id,
      title: chapter.title,
      paragraphs: chapter.paragraphs.map((paragraph) => ({
        id: paragraph.id,
        type: paragraph.type,
        content: paragraph.content,
        sentences: (paragraph.sentences ?? []).map((sentence) => ({
          id: sentence.id,
          content: sentence.content,
        })),
      })),
    })),
  });

  const resources = {
    text: {
      path: "text/book.json",
      sha256: await sha256ForFile(bookJsonPath),
    },
  };

  const coverPath = path.join(projectPaths.assetsDir, "images", "cover.png");
  if (await exists(coverPath)) {
    const targetDir = path.join(buildRoot, "images");
    await ensureDir(targetDir);
    await fsp.copyFile(coverPath, path.join(targetDir, "cover.png"));
    resources.cover = {
      path: "images/cover.png",
      sha256: await sha256ForFile(path.join(targetDir, "cover.png")),
    };
  }

  const flipSoundPath = path.join(projectPaths.assetsDir, "sound", "page_flip.wav.ogg");
  if (await exists(flipSoundPath)) {
    const targetDir = path.join(buildRoot, "sound");
    await ensureDir(targetDir);
    await fsp.copyFile(flipSoundPath, path.join(targetDir, "page_flip.wav.ogg"));
    resources.flipSound = {
      path: "sound/page_flip.wav.ogg",
      sha256: await sha256ForFile(path.join(targetDir, "page_flip.wav.ogg")),
    };
  }

  const narrationSourceDir = path.join(projectPaths.assetsDir, "audio", "narration");
  if (await exists(narrationSourceDir)) {
    const narrationTargetDir = path.join(buildRoot, "audio", "narration");
    await copyDirRecursive(narrationSourceDir, narrationTargetDir);
    resources.narration = {
      dir: "audio/narration",
      codec: "mixed",
    };
  }

  await writeJson(path.join(buildRoot, "manifest.json"), {
    formatVersion: project.metadata.formatVersion,
    packId: project.metadata.packId,
    packVersion: project.metadata.packVersion,
    book: {
      title: project.metadata.title,
      author: project.metadata.author,
      edition: project.metadata.edition,
    },
    resources,
  });

  const artifactName = `${project.metadata.packId}_v${project.metadata.packVersion}.hbs.zip`;
  const artifactPath = path.join(distDir, artifactName);
  await new Promise((resolve, reject) => {
    const zipFile = new yazl.ZipFile();
    const output = fs.createWriteStream(artifactPath);
    output.on("close", resolve);
    output.on("error", reject);
    zipFile.outputStream.pipe(output);
    addDirToZip(zipFile, buildRoot);
    zipFile.end();
  });

  project.lastBuild = {
    buildId,
    artifactPath: path.relative(projectPaths.projectDir, artifactPath),
    builtAt: new Date().toISOString(),
  };

  return {
    buildId,
    artifactName,
    artifactRelativePath: project.lastBuild.artifactPath,
  };
}
