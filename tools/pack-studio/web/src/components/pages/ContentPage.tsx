import { useEffect, useState, useCallback } from "react";
import { useParams } from "react-router-dom";
import { BookOpen, ChevronDown, ChevronRight, Quote } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { api } from "@/lib/api";
import type { StructuredBook, Chapter } from "@/types";

function ChapterCard({ chapter, index }: { chapter: Chapter; index: number }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <Card>
      <CardHeader className="pb-2 cursor-pointer" onClick={() => setExpanded(!expanded)}>
        <div className="flex items-center gap-2">
          {expanded ? <ChevronDown className="w-4 h-4 text-muted-foreground" /> : <ChevronRight className="w-4 h-4 text-muted-foreground" />}
          <CardTitle className="text-sm font-medium">
            {chapter.title || `第${index + 1}章`}
          </CardTitle>
          <Badge variant="outline" className="text-[10px]">
            {chapter.paragraphs.length} 段
          </Badge>
        </div>
      </CardHeader>
      {expanded && (
        <CardContent className="pt-0 space-y-3">
          {chapter.paragraphs.map((paragraph) => (
            <div key={paragraph.id} className="border rounded-lg p-3 space-y-2">
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <Quote className="w-3 h-3" />
                <span>{paragraph.id}</span>
                <span>·</span>
                <span>{paragraph.sentences?.length || 0} 句</span>
              </div>
              <p className="text-sm leading-relaxed">{paragraph.content}</p>
              {paragraph.sentences && paragraph.sentences.length > 0 && (
                <div className="space-y-1 pl-3 border-l-2 border-muted">
                  {paragraph.sentences.map((sentence) => (
                    <div key={sentence.id} className="text-xs text-muted-foreground flex gap-2">
                      <span className="font-mono text-[10px] shrink-0">{sentence.id}</span>
                      <span>{sentence.content}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </CardContent>
      )}
    </Card>
  );
}

export function ContentPage() {
  const { id } = useParams<{ id: string }>();
  const [book, setBook] = useState<StructuredBook | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    if (!id) return;
    try {
      const data = await api.getProject(id);
      setBook(data.structuredBook);
    } catch {
      setBook(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) {
    return <div className="text-muted-foreground">加载中...</div>;
  }

  if (!book) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-muted-foreground gap-2">
        <BookOpen className="w-10 h-10 opacity-40" />
        <p>尚未标准化，请先在导入页执行标准化。</p>
      </div>
    );
  }

  const totalParagraphs = book.chapters.reduce((sum, c) => sum + c.paragraphs.length, 0);
  const totalSentences = book.chapters.reduce(
    (sum, c) =>
      sum + c.paragraphs.reduce((ps, p) => ps + (p.sentences?.length || 0), 0),
    0
  );

  return (
    <div className="max-w-4xl mx-auto space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">{book.book.title}</h1>
          <p className="text-sm text-muted-foreground">
            {book.book.author} · {book.book.edition}
          </p>
        </div>
        <div className="flex gap-2">
          <Badge variant="outline">{book.chapters.length} 章</Badge>
          <Badge variant="outline">{totalParagraphs} 段</Badge>
          <Badge variant="outline">{totalSentences} 句</Badge>
        </div>
      </div>

      <ScrollArea className="h-[calc(100vh-220px)]">
        <div className="space-y-3 pr-4">
          {book.chapters.map((chapter, index) => (
            <ChapterCard key={chapter.id} chapter={chapter} index={index} />
          ))}
        </div>
      </ScrollArea>
    </div>
  );
}
