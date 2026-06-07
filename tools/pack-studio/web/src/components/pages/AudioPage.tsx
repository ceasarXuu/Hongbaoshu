import { useEffect, useState, useCallback } from "react";
import { useParams } from "react-router-dom";
import { Download, Upload, AlertTriangle, CheckCircle, FileX } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { api } from "@/lib/api";
import type { AudioMappingResult, Project } from "@/types";

export function AudioPage() {
  const { id } = useParams<{ id: string }>();
  const [project, setProject] = useState<Project | null>(null);
  const [audioResult, setAudioResult] = useState<AudioMappingResult | null>(null);
  const [sourceDir, setSourceDir] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    if (!id) return;
    const data = await api.getProject(id);
    setProject(data.project);
    setAudioResult(data.project.audioMapping || null);
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleExport() {
    if (!id) return;
    setLoading(true);
    setMessage("");
    try {
      const result = await api.exportSentences(id);
      setMessage(`已导出 ${result.sentenceCount} 条句子到 ${result.fileName}`);
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "导出失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleImport() {
    if (!id || !sourceDir.trim()) return;
    setLoading(true);
    setMessage("");
    try {
      const result = await api.importNarration(id, sourceDir.trim());
      setAudioResult(result);
      setMessage(`导入完成：覆盖率 ${(result.coverage * 100).toFixed(1)}%`);
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "导入失败");
    } finally {
      setLoading(false);
    }
  }

  if (!project) {
    return <div className="text-muted-foreground">加载中...</div>;
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <h1 className="text-2xl font-semibold">音频管理</h1>

      <div className="grid grid-cols-3 gap-4">
        <Card>
          <CardContent className="p-4 flex items-center gap-3">
            <CheckCircle className="w-5 h-5 text-green-600" />
            <div>
              <div className="text-2xl font-bold">{audioResult?.matchedCount ?? 0}</div>
              <div className="text-xs text-muted-foreground">已匹配</div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 flex items-center gap-3">
            <AlertTriangle className="w-5 h-5 text-amber-600" />
            <div>
              <div className="text-2xl font-bold">{audioResult?.missingCount ?? 0}</div>
              <div className="text-xs text-muted-foreground">缺失</div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 flex items-center gap-3">
            <FileX className="w-5 h-5 text-red-600" />
            <div>
              <div className="text-2xl font-bold">{audioResult?.orphanCount ?? 0}</div>
              <div className="text-xs text-muted-foreground">孤儿音频</div>
            </div>
          </CardContent>
        </Card>
      </div>

      {audioResult && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">覆盖率</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <Progress value={audioResult.coverage * 100} />
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">
                {audioResult.matchedCount} / {audioResult.matchedCount + audioResult.missingCount}
              </span>
              <span className="font-medium">{(audioResult.coverage * 100).toFixed(1)}%</span>
            </div>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">操作</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex gap-3">
            <Button onClick={handleExport} disabled={loading}>
              <Download className="w-4 h-4 mr-1" />
              导出句子 CSV
            </Button>
          </div>
          <div className="flex gap-3">
            <Input
              value={sourceDir}
              onChange={(e) => setSourceDir(e.target.value)}
              placeholder=" narration 目录绝对路径，例如 D:\\audio\\narration"
              className="flex-1"
            />
            <Button variant="secondary" onClick={handleImport} disabled={loading || !sourceDir.trim()}>
              <Upload className="w-4 h-4 mr-1" />
              导入音频目录
            </Button>
          </div>
          {message && (
            <div className={`text-sm ${message.includes("失败") || message.includes("错误") ? "text-red-600" : "text-green-600"}`}>
              {message}
            </div>
          )}
        </CardContent>
      </Card>

      {audioResult && audioResult.missingSentenceIds.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base text-red-600">缺失句子</CardTitle>
          </CardHeader>
          <CardContent>
            <ScrollArea className="h-48">
              <div className="flex flex-wrap gap-2">
                {audioResult.missingSentenceIds.map((sid) => (
                  <Badge key={sid} variant="destructive" className="text-[10px]">
                    {sid}
                  </Badge>
                ))}
              </div>
            </ScrollArea>
          </CardContent>
        </Card>
      )}

      {audioResult && audioResult.orphanFiles.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base text-amber-600">孤儿音频文件</CardTitle>
          </CardHeader>
          <CardContent>
            <ScrollArea className="h-48">
              <div className="space-y-1">
                {audioResult.orphanFiles.map((file) => (
                  <div key={file} className="text-xs text-muted-foreground font-mono">
                    {file}
                  </div>
                ))}
              </div>
            </ScrollArea>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
