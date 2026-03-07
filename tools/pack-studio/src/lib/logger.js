import fs from "node:fs/promises";
import path from "node:path";

import { ensureDir, writeJson } from "./files.js";

function stamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

export async function runLoggedTask(projectPaths, taskType, handler) {
  const taskId = `${taskType}-${stamp()}`;
  const logPath = path.join(projectPaths.logsDir, `${taskId}.log`);
  const reportPath = path.join(projectPaths.reportsDir, `${taskId}.json`);
  const startedAt = new Date().toISOString();

  await ensureDir(projectPaths.logsDir);
  await ensureDir(projectPaths.reportsDir);

  const log = async (level, message) => {
    await fs.appendFile(logPath, `[${level}] [${taskType}] ${message}\n`, "utf8");
  };

  await log("INFO", `task started at ${startedAt}`);

  try {
    const result = await handler({ log });
    const finishedAt = new Date().toISOString();
    await log("INFO", `task finished at ${finishedAt}`);
    await writeJson(reportPath, {
      taskType,
      taskId,
      startedAt,
      finishedAt,
      status: "success",
      result,
    });
    return result;
  } catch (error) {
    const finishedAt = new Date().toISOString();
    await log("ERROR", error.message);
    await writeJson(reportPath, {
      taskType,
      taskId,
      startedAt,
      finishedAt,
      status: "failed",
      error: {
        message: error.message,
        stack: error.stack,
      },
    });
    throw error;
  }
}
