import { useEffect, useState, useCallback } from "react";
import { useParams } from "react-router-dom";
import { Upload, Save, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { api } from "@/lib/api";
import type { Project, ProjectMetadata } from "@/types";

export function ImportPage() {
  const { id } = useParams<{ id: string }>();
  const [project, setProject] = useState<Project | null>(null);
  const [metadata, setMetadata] = useState<ProjectMetadata>({
    packId: "",
    packVersion: 1,
    formatVersion: 1,
    title: "",
    author: "",
    edition: "1.0",
  });
  const [text, setText] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    if (!id) return;
    const data = await api.getProject(id);
    setProject(data.project);
    setMetadata(data.project.metadata);
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleImport() {
    if (!id) return;
    setLoading(true);
    setMessage("");
    try {
      await api.importText(id, metadata, text);
      setMessage("导入成功");
      await load();
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "导入失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleNormalize() {
    if (!id) return;
    setLoading(true);
    setMessage("");
    try {
      const result = await api.normalize(id);
      setMessage(`标准化完成：${result.chapters} 章 / ${result.paragraphs} 段 / ${result.sentences} 句`);
      await load();
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "标准化失败");
    } finally {
      setLoading(false);
    }
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) {
      file.text().then(setText);
    }
  }

  if (!project) {
    return <div className="text-muted-foreground">加载中...</div>;
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">导入与标准化</h1>
        <Badge variant="secondary">{project.status}</Badge>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">书籍元数据</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label>packId</Label>
            <Input value={metadata.packId} onChange={(e) => setMetadata({ ...metadata, packId: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>packVersion</Label>
            <Input
              type="number"
              value={metadata.packVersion}
              onChange={(e) => setMetadata({ ...metadata, packVersion: Number(e.target.value) })}
            />
          </div>
          <div className="space-y-2">
            <Label>书名</Label>
            <Input value={metadata.title} onChange={(e) => setMetadata({ ...metadata, title: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>作者</Label>
            <Input value={metadata.author} onChange={(e) => setMetadata({ ...metadata, author: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>版本</Label>
            <Input value={metadata.edition} onChange={(e) => setMetadata({ ...metadata, edition: e.target.value })} />
          </div>
          <div className="space-y-2">
            <Label>formatVersion</Label>
            <Input
              type="number"
              value={metadata.formatVersion}
              onChange={(e) => setMetadata({ ...metadata, formatVersion: Number(e.target.value) })}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">文本内容</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center gap-3">
            <Label className="flex items-center gap-2 cursor-pointer">
              <Upload className="w-4 h-4" />
              <span>上传 txt 文件</span>
              <input type="file" accept=".txt,text/plain" className="hidden" onChange={handleFileChange} />
            </Label>
            {text.length > 0 && (
              <span className="text-xs text-muted-foreground">{text.length} 字符</span>
            )}
          </div>
          <Textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="粘贴 txt 内容，或通过上方选择器导入文件..."
            className="min-h-[240px]"
          />
          <div className="flex gap-3">
            <Button onClick={handleImport} disabled={loading || !text.trim()}>
              <Save className="w-4 h-4 mr-1" />
              保存文本
            </Button>
            <Button variant="secondary" onClick={handleNormalize} disabled={loading}>
              <Sparkles className="w-4 h-4 mr-1" />
              标准化
            </Button>
          </div>
          {message && (
            <div className={`text-sm ${message.includes("失败") || message.includes("错误") ? "text-red-600" : "text-green-600"}`}>
              {message}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
