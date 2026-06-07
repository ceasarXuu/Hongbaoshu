import { useEffect, useState, useCallback } from "react";
import { useParams } from "react-router-dom";
import { ShieldCheck, XCircle, AlertTriangle, Info, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

import { api } from "@/lib/api";
import type { ValidationResult } from "@/types";

export function ValidationPage() {
  const { id } = useParams<{ id: string }>();
  const [result, setResult] = useState<ValidationResult | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    try {
      const data = await api.validate(id);
      setResult(data);
    } catch (e) {
      setResult(null);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleValidate() {
    if (!id) return;
    setLoading(true);
    try {
      const data = await api.validate(id);
      setResult(data);
    } catch (e) {
      setResult(null);
    } finally {
      setLoading(false);
    }
  }

  const errors = result?.items.filter((i) => i.level === "error") || [];
  const warnings = result?.items.filter((i) => i.level === "warning") || [];
  const infos = result?.items.filter((i) => i.level === "info") || [];

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">校验</h1>
        <Button onClick={handleValidate} disabled={loading}>
          <RotateCcw className="w-4 h-4 mr-1" />
          重新校验
        </Button>
      </div>

      {result && (
        <div className="grid grid-cols-4 gap-4">
          <Card className={result.status === "passed" ? "border-green-300" : "border-red-300"}>
            <CardContent className="p-4 flex items-center gap-3">
              {result.status === "passed" ? (
                <ShieldCheck className="w-5 h-5 text-green-600" />
              ) : (
                <XCircle className="w-5 h-5 text-red-600" />
              )}
              <div>
                <div className="text-sm font-medium">{result.status === "passed" ? "通过" : "未通过"}</div>
                <div className="text-xs text-muted-foreground">校验状态</div>
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4 flex items-center gap-3">
              <XCircle className="w-5 h-5 text-red-600" />
              <div>
                <div className="text-2xl font-bold">{result.summary.errors}</div>
                <div className="text-xs text-muted-foreground">错误</div>
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4 flex items-center gap-3">
              <AlertTriangle className="w-5 h-5 text-amber-600" />
              <div>
                <div className="text-2xl font-bold">{result.summary.warnings}</div>
                <div className="text-xs text-muted-foreground">警告</div>
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4 flex items-center gap-3">
              <Info className="w-5 h-5 text-blue-600" />
              <div>
                <div className="text-2xl font-bold">{result.summary.sentences}</div>
                <div className="text-xs text-muted-foreground">句子数</div>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {errors.length > 0 && (
        <Card className="border-red-200">
          <CardHeader>
            <CardTitle className="text-base text-red-600 flex items-center gap-2">
              <XCircle className="w-4 h-4" />
              错误 ({errors.length})
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {errors.map((item, idx) => (
              <div key={idx} className="flex gap-3 text-sm">
                <Badge variant="destructive" className="shrink-0 h-5 text-[10px]">
                  {item.code}
                </Badge>
                <span>{item.message}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {warnings.length > 0 && (
        <Card className="border-amber-200">
          <CardHeader>
            <CardTitle className="text-base text-amber-600 flex items-center gap-2">
              <AlertTriangle className="w-4 h-4" />
              警告 ({warnings.length})
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {warnings.map((item, idx) => (
              <div key={idx} className="flex gap-3 text-sm">
                <Badge variant="outline" className="shrink-0 h-5 text-[10px] border-amber-300 text-amber-700">
                  {item.code}
                </Badge>
                <span>{item.message}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {infos.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <Info className="w-4 h-4" />
              提示 ({infos.length})
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {infos.map((item, idx) => (
              <div key={idx} className="flex gap-3 text-sm text-muted-foreground">
                <span className="font-mono text-[10px] shrink-0 bg-muted px-1.5 py-0.5 rounded">{item.code}</span>
                <span>{item.message}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {result && errors.length === 0 && warnings.length === 0 && infos.length === 0 && (
        <div className="flex flex-col items-center justify-center py-12 text-green-600 gap-2">
          <ShieldCheck className="w-10 h-10" />
          <p className="font-medium">所有检查项均通过</p>
        </div>
      )}
    </div>
  );
}
