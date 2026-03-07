import express from "express";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { ProjectStore } from "./lib/project-store.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const appRoot = path.resolve(__dirname, "..");
const staticDir = path.join(appRoot, "src", "static");
const port = Number(process.env.PORT || 4310);

const store = new ProjectStore(appRoot);
await store.init();

const app = express();
app.use(express.json({ limit: "10mb" }));
app.use(express.static(staticDir));

app.get("/api/health", (_req, res) => {
  res.json({ status: "ok" });
});

app.get("/api/projects", async (_req, res, next) => {
  try {
    res.json(await store.listProjects());
  } catch (error) {
    next(error);
  }
});

app.post("/api/projects", async (req, res, next) => {
  try {
    res.status(201).json(await store.createProject(req.body ?? {}));
  } catch (error) {
    next(error);
  }
});

app.get("/api/projects/:projectId", async (req, res, next) => {
  try {
    const project = await store.getProject(req.params.projectId);
    if (!project) {
      res.status(404).json({ error: "Project not found." });
      return;
    }
    const structuredBook = await store.structuredBook(req.params.projectId).catch(() => null);
    res.json({ project, structuredBook });
  } catch (error) {
    next(error);
  }
});

app.post("/api/projects/:projectId/import-text", async (req, res, next) => {
  try {
    res.json(await store.importText(req.params.projectId, req.body ?? {}));
  } catch (error) {
    next(error);
  }
});

app.post("/api/projects/:projectId/normalize", async (req, res, next) => {
  try {
    res.json(await store.normalizeProject(req.params.projectId));
  } catch (error) {
    next(error);
  }
});

app.post("/api/projects/:projectId/export-sentences", async (req, res, next) => {
  try {
    res.json(await store.exportSentences(req.params.projectId));
  } catch (error) {
    next(error);
  }
});

app.post("/api/projects/:projectId/import-narration", async (req, res, next) => {
  try {
    res.json(await store.importNarration(req.params.projectId, req.body?.sourceDir));
  } catch (error) {
    next(error);
  }
});

app.post("/api/projects/:projectId/validate", async (req, res, next) => {
  try {
    res.json(await store.validate(req.params.projectId));
  } catch (error) {
    next(error);
  }
});

app.post("/api/projects/:projectId/build", async (req, res, next) => {
  try {
    res.json(await store.build(req.params.projectId));
  } catch (error) {
    next(error);
  }
});

app.get("/api/projects/:projectId/artifact", async (req, res, next) => {
  try {
    const project = await store.getProject(req.params.projectId);
    if (!project?.lastBuild?.artifactPath) {
      res.status(404).json({ error: "Artifact not found." });
      return;
    }
    const artifactPath = path.join(store.projectPaths(project.id).projectDir, project.lastBuild.artifactPath);
    res.download(artifactPath);
  } catch (error) {
    next(error);
  }
});

app.use((error, _req, res, _next) => {
  res.status(500).json({ error: error.message });
});

app.listen(port, () => {
  console.log(`Pack Studio listening on http://localhost:${port}`);
});
