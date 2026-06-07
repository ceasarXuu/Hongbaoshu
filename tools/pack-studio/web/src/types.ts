export interface Project {
  id: string;
  name: string;
  status: string;
  sourceType: string;
  createdAt: string;
  updatedAt: string;
  metadata: ProjectMetadata;
  exports?: { latestSentencesCsv?: string };
  audioMapping?: AudioMappingResult;
  lastValidation?: ValidationResult;
  lastBuild?: { buildId: string; artifactPath: string; builtAt: string };
}

export interface ProjectMetadata {
  packId: string;
  packVersion: number;
  formatVersion: number;
  title: string;
  author: string;
  edition: string;
}

export interface Sentence {
  id: string;
  content: string;
  chapterId: string;
  paragraphId: string;
  indexInParagraph: number;
  indexInBook: number;
}

export interface Paragraph {
  id: string;
  type: string;
  content: string;
  sentences?: Sentence[];
}

export interface Chapter {
  id: string;
  title: string;
  paragraphs: Paragraph[];
}

export interface StructuredBook {
  book: { title: string; author: string; edition: string };
  chapters: Chapter[];
}

export interface ProjectDetail {
  project: Project;
  structuredBook: StructuredBook | null;
}

export interface AudioMappingResult {
  importedDirectory: string;
  matchedCount: number;
  missingCount: number;
  orphanCount: number;
  duplicatePrefixCount: number;
  coverage: number;
  missingSentenceIds: string[];
  orphanFiles: string[];
  duplicatePrefixes: string[];
}

export interface ValidationItem {
  level: "error" | "warning" | "info";
  code: string;
  message: string;
}

export interface ValidationResult {
  status: "passed" | "failed";
  summary: {
    errors: number;
    warnings: number;
    infos: number;
    chapters: number;
    paragraphs: number;
    sentences: number;
  };
  items: ValidationItem[];
}

export interface BuildResult {
  buildId: string;
  artifactName: string;
  artifactRelativePath: string;
}
