import { Link, useParams, useLocation } from "react-router-dom";
import { useEffect, useState } from "react";
import {
  LayoutDashboard,
  FileText,
  BookOpen,
  Headphones,
  ShieldCheck,
  Package,
  Plus,
} from "lucide-react";
import { Button } from "@/components/ui/button";
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
    <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${map[status] || map.draft}`}>
      {status}
    </span>
  );
}

export function AppLayout({ children }: { children: React.ReactNode }) {
  const { id: projectId } = useParams<{ id: string }>();
  const location = useLocation();
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProject, setSelectedProject] = useState<Project | null>(null);
  const [createName, setCreateName] = useState("");

  useEffect(() => {
    api.listProjects().then(setProjects).catch(() => setProjects([]));
  }, []);

  useEffect(() => {
    if (projectId) {
      api.getProject(projectId)
        .then((d) => setSelectedProject(d.project))
        .catch(() => setSelectedProject(null));
    } else {
      setSelectedProject(null);
    }
  }, [projectId]);

  async function handleCreate() {
    const name = createName.trim() || undefined;
    const project = await api.createProject(name || "新项目");
    setProjects((prev) => [project, ...prev]);
    setCreateName("");
  }

  const isProjectPage = !!projectId;

  const navItems = isProjectPage
    ? [
        { to: `/projects/${projectId}/import`, label: "导入", icon: FileText },
        { to: `/projects/${projectId}/content`, label: "内容", icon: BookOpen },
        { to: `/projects/${projectId}/audio`, label: "音频", icon: Headphones },
        { to: `/projects/${projectId}/validate`, label: "校验", icon: ShieldCheck },
        { to: `/projects/${projectId}/build`, label: "构建", icon: Package },
      ]
    : [];

  return (
    <div className="flex h-screen bg-background">
      {/* Sidebar */}
      <aside className="w-72 border-r bg-card flex flex-col shrink-0">
        <div className="p-4 border-b">
          <Link to="/" className="flex items-center gap-2 text-lg font-semibold text-foreground hover:opacity-80">
            <LayoutDashboard className="w-5 h-5" />
            Pack Studio
          </Link>
        </div>

        <div className="p-3">
          <div className="flex gap-2">
            <input
              value={createName}
              onChange={(e) => setCreateName(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleCreate()}
              placeholder="新建项目名称"
              className="flex-1 h-8 px-2 text-sm rounded-md border bg-background"
            />
            <Button size="sm" onClick={handleCreate}>
              <Plus className="w-4 h-4" />
            </Button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-3 pb-3 space-y-1">
          {projects.map((p) => (
            <Link
              key={p.id}
              to={`/projects/${p.id}/import`}
              className={`flex items-center justify-between px-3 py-2 rounded-lg text-sm transition-colors ${
                p.id === projectId ? "bg-accent text-accent-foreground" : "hover:bg-muted"
              }`}
            >
              <span className="truncate">{p.name}</span>
              <StatusBadge status={p.status} />
            </Link>
          ))}
        </div>

        {isProjectPage && selectedProject && (
          <div className="border-t p-3 space-y-1">
            <div className="text-xs font-medium text-muted-foreground mb-2 px-1">{selectedProject.name}</div>
            {navItems.map((item) => {
              const active = location.pathname === item.to;
              const Icon = item.icon;
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-sm transition-colors ${
                    active ? "bg-primary text-primary-foreground" : "hover:bg-muted"
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  {item.label}
                </Link>
              );
            })}
          </div>
        )}
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-y-auto p-6">{children}</main>
    </div>
  );
}
