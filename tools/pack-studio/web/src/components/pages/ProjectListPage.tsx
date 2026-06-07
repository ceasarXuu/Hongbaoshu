import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { FolderOpen, Clock } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { api } from "@/lib/api";
import type { Project } from "@/types";

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, string> = {
    draft: "bg-muted text-muted-foreground",
    imported: "bg-blue-100 text-blue-700",
    normalized: "bg-amber-100 text-amber-700",
    audio_pending: "bg-purple-100 text-purple-700",
    ready_to_build: "bg-green-100 text-green-700",
    build_failed: "bg-red-100 text-red-700",
    built: "bg-emerald-100 text-emerald-700",
  };
  return (
    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${map[status] || map.draft}`}>
      {status}
    </span>
  );
}

export function ProjectListPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.listProjects().then((data) => {
      setProjects(data);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  if (loading) {
    return <div className="text-muted-foreground">加载中...</div>;
  }

  if (projects.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-muted-foreground gap-2">
        <FolderOpen className="w-10 h-10 opacity-40" />
        <p>暂无项目，请从左侧创建。</p>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto space-y-4">
      <h1 className="text-2xl font-semibold">项目列表</h1>
      <div className="grid gap-3">
        {projects.map((p) => (
          <Link key={p.id} to={`/projects/${p.id}/import`}>
            <Card className="hover:shadow-md transition-shadow cursor-pointer">
              <CardContent className="p-4 flex items-center justify-between">
                <div className="space-y-1">
                  <div className="font-medium">{p.name}</div>
                  <div className="text-xs text-muted-foreground flex items-center gap-3">
                    <span>来源: {p.sourceType}</span>
                    <span className="flex items-center gap-1">
                      <Clock className="w-3 h-3" />
                      {new Date(p.updatedAt).toLocaleString()}
                    </span>
                  </div>
                </div>
                <StatusBadge status={p.status} />
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
