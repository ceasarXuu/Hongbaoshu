import { useEffect, useState, useCallback } from "react";
import { useParams } from "react-router-dom";
import { Package, Play, Download, CheckCircle, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { api } from "@/lib/api";
import type { Project, BuildResult } from "@/types";

export function BuildPage() {
  const { id } = useParams<{ id: string }>();
  const [project, setProject] = useState<Project | null>(null);
  const [buildResult, setBuildResult] = useState<BuildResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    if (!id) return;
    const data = await api.getProject(id);
    setProject(data.project);
    if (data.project.lastBuild) {
      setBuildResult({
        buildId: data.project.lastBuild.buildId,
        artifactName: data.project.lastBuild.artifactPath.split("/").pop() || "",
        artifactRelativePath: data.project.lastBuild.artifactPath,
      });
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleBuild() {
    if (!id) return;
    setLoading(true);
    setMessage("");
    try {
      const result = await api.build(id);
      setBuildResult(result);
      setMessage(`构建成功：${result.artifactName}`);
      await load();
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "构建失败");
    } finally {
      setLoading(false);
    }
  }

  if (!project) {
    return <div className="text-muted-foreground">加载中...</div>;
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">构建</h1>
        <Badge variant={project.status === "ready_to_build" || project.status === "built" ? "default" : "secondary"}>
          {project.status}
        </Badge>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">构建检查</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex items-center gap-3 text-sm">
            {project.status === "ready_to_build" || project.status === "built" ? (
              <CheckCircle className="w-4 h-4 text-green-600" />
            ) : (
              <XCircle className="w-4 h-4 text-red-600" />
            )}
            <span>
              {project.status === "ready_to_build" || project.status === "built"
                ? "项目已通过校验，可以构建"
                : "项目尚未通过校验，请先在校验页执行全量校验"}
            </span>
          </div>
          <div className="flex gap-3">
            <Button onClick={handleBuild} disabled={loading}>
              <Play className="w-4 h-4 mr-1" />
              开始构建
            </Button>
            {buildResult && (
              <a
                href={api.downloadArtifact(project.id)}
                target="_blank"
                rel="noopener noreferrer"
              >
                <Button variant="secondary">
                  <Download className="w-4 h-4 mr-1" />
                  下载产物
                </Button>
              </a>
            )}
          </div>
          {message && (
            <div className={`text-sm ${message.includes("失败") || message.includes("错误") ? "text-red-600" : "text-green-600"}`}>
              {message}
            </div>
          )}
        </CardContent>
      </Card>

      {buildResult && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">最近一次构建</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">构建 ID</span>
              <span className="font-mono">{buildResult.buildId}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">产物名称</span>
              <span className="font-mono">{buildResult.artifactName}</span>
            </div>
          </CardContent>
        </Card>
      )}

      {!buildResult && (
        <div className="flex flex-col items-center justify-center py-12 text-muted-foreground gap-2">
          <Package className="w-10 h-10 opacity-40" />
          <p>尚未构建，点击上方按钮开始。</p>
        </div>
      )}
    </div>
  );
}
