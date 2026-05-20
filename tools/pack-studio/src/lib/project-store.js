import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";

import { importNarrationDirectory } from "./audio-scan.js";
import { buildPack } from "./build-pack.js";
import { exportSentencesCsv } from "./csv-export.js";
import { ensureDir, listDirs, readJson, writeJson, writeText } from "./files.js";
import { runLoggedTask } from "./logger.js";
import { normalizeTxt } from "./normalize.js";
import { validateProject } from "./validator.js";

export class ProjectStore {
  constructor(rootDir) {
    this.rootDir = rootDir;
    this.projectsRoot = path.join(rootDir, "workspace", "projects");
  }

  async init() {
    await ensureDir(this.projectsRoot);
  }

  projectPaths(projectId) {
    const projectDir = path.join(this.projectsRoot, projectId);
    return {
      projectDir,
      projectFile: path.join(projectDir, "project.json"),
      sourceDir: path.join(projectDir, "source"),
      normalizedDir: path.join(projectDir, "normalized"),
      exportsDir: path.join(projectDir, "exports"),
      assetsDir: path.join(projectDir, "assets"),
      buildDir: path.join(projectDir, "build"),
      reportsDir: path.join(projectDir, "reports"),
      logsDir: path.join(projectDir, "logs"),
    };
  }

  async createProject({ name }) {
    const projectId = `proj_${Date.now()}_${crypto.randomBytes(3).toString("hex")}`;
    const paths = this.projectPaths(projectId);
    await ensureDir(paths.projectDir);
    await ensureDir(paths.sourceDir);
    await ensureDir(paths.normalizedDir);
    await ensureDir(paths.exportsDir);
    await ensureDir(paths.assetsDir);
    await ensureDir(paths.buildDir);
    await ensureDir(paths.reportsDir);
    await ensureDir(paths.logsDir);

    const safeName = name?.trim() || projectId;
    const project = {
      id: projectId,
      name: safeName,
      status: "draft",
      sourceType: "txt",
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      metadata: {
        packId: `local.${projectId}`,
        packVersion: 1,
        formatVersion: 1,
        title: safeName,
        author: "未知作者",
        edition: "1.0",
      },
    };
    await writeJson(paths.projectFile, project);
    return project;
  }

  async listProjects() {
    const projectIds = await listDirs(this.projectsRoot);
    const projects = await Promise.all(projectIds.map((projectId) => this.getProject(projectId)));
    return projects.filter(Boolean).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  }

  async getProject(projectId) {
    return readJson(this.projectPaths(projectId).projectFile);
  }

  async saveProject(project) {
    project.updatedAt = new Date().toISOString();
    await writeJson(this.projectPaths(project.id).projectFile, project);
    return project;
  }

  async importText(projectId, payload) {
    const project = await this.getProject(projectId);
    if (!project) {
      throw new Error("Project not found.");
    }
    const paths = this.projectPaths(projectId);
    return runLoggedTask(paths, "import-text", async ({ log }) => {
      const metadata = {
        ...project.metadata,
        ...payload.metadata,
        packVersion: Number(payload.metadata?.packVersion ?? project.metadata.packVersion),
        formatVersion: Number(payload.metadata?.formatVersion ?? project.metadata.formatVersion),
      };
      project.metadata = metadata;
      project.name = payload.name?.trim() || project.name;
      project.sourceType = "txt";
      project.status = "imported";
      await writeText(path.join(paths.sourceDir, "input.txt"), payload.text ?? "");
      await this.saveProject(project);
      await log("INFO", `source text length: ${(payload.text ?? "").length}`);
      return { status: "imported" };
    });
  }

  async normalizeProject(projectId) {
    const project = await this.getProject(projectId);
    if (!project) {
      throw new Error("Project not found.");
    }
    const paths = this.projectPaths(projectId);
    return runLoggedTask(paths, "normalize", async ({ log }) => {
      const sourceText = await fs.readFile(path.join(paths.sourceDir, "input.txt"), "utf8");
      const structuredBook = normalizeTxt(sourceText, project.metadata);
      await writeJson(path.join(paths.normalizedDir, "structured_book.json"), structuredBook);
      project.status = "normalized";
      await this.saveProject(project);
      const paragraphs = structuredBook.chapters.reduce((sum, chapter) => sum + chapter.paragraphs.length, 0);
      const sentences = structuredBook.chapters.reduce(
        (sum, chapter) =>
          sum +
          chapter.paragraphs.reduce((paragraphSum, paragraph) => paragraphSum + (paragraph.sentences?.length ?? 0), 0),
        0,
      );
      await log("INFO", `generated ${structuredBook.chapters.length} chapters`);
      await log("INFO", `generated ${paragraphs} paragraphs`);
      await log("INFO", `generated ${sentences} sentences`);
      return { chapters: structuredBook.chapters.length, paragraphs, sentences };
    });
  }

  async structuredBook(projectId) {
    return readJson(path.join(this.projectPaths(projectId).normalizedDir, "structured_book.json"));
  }

  async exportSentences(projectId) {
    const project = await this.getProject(projectId);
    const paths = this.projectPaths(projectId);
    const structuredBook = await this.structuredBook(projectId);
    if (!project || !structuredBook) {
      throw new Error("Project or normalized book not found.");
    }
    const result = await runLoggedTask(paths, "export-sentences", async ({ log }) => {
      const exportResult = await exportSentencesCsv(paths, project, structuredBook);
      await log("INFO", `exported ${exportResult.sentenceCount} sentences to ${exportResult.fileName}`);
      return exportResult;
    });
    await this.saveProject(project);
    return result;
  }

  async importNarration(projectId, sourceDir) {
    const project = await this.getProject(projectId);
    const paths = this.projectPaths(projectId);
    const structuredBook = await this.structuredBook(projectId);
    if (!project || !structuredBook) {
      throw new Error("Project or normalized book not found.");
    }
    const result = await runLoggedTask(paths, "audio-scan", async ({ log }) => {
      const scanResult = await importNarrationDirectory(paths, structuredBook, sourceDir);
      await log("INFO", `matched=${scanResult.matchedCount} missing=${scanResult.missingCount}`);
      await log("INFO", `orphan=${scanResult.orphanCount} duplicate=${scanResult.duplicatePrefixCount}`);
      return scanResult;
    });
    project.audioMapping = result;
    await this.saveProject(project);
    return result;
  }

  async validate(projectId) {
    const project = await this.getProject(projectId);
    const paths = this.projectPaths(projectId);
    const structuredBook = await this.structuredBook(projectId);
    if (!project || !structuredBook) {
      throw new Error("Project or normalized book not found.");
    }
    const result = await runLoggedTask(paths, "validate", async ({ log }) => {
      const validation = validateProject(project, structuredBook);
      await log("INFO", `validation status: ${validation.status}`);
      await log(
        "INFO",
        `errors=${validation.summary.errors} warnings=${validation.summary.warnings} sentences=${validation.summary.sentences}`,
      );
      return validation;
    });
    project.lastValidation = result;
    project.status = result.status === "passed" ? "ready_to_build" : "imported";
    await this.saveProject(project);
    return result;
  }

  async build(projectId) {
    const project = await this.getProject(projectId);
    const paths = this.projectPaths(projectId);
    const structuredBook = await this.structuredBook(projectId);
    if (!project || !structuredBook) {
      throw new Error("Project or normalized book not found.");
    }

    const validation = validateProject(project, structuredBook);
    if (validation.status !== "passed") {
      throw new Error("Build blocked because validation failed.");
    }

    const result = await runLoggedTask(paths, "build", async ({ log }) => {
      const buildResult = await buildPack(paths, project, structuredBook);
      await log("INFO", `artifact generated: ${buildResult.artifactName}`);
      return buildResult;
    });
    project.status = "built";
    await this.saveProject(project);
    return result;
  }
}
