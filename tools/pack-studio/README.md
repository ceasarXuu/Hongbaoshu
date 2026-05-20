# Pack Studio

Pack Studio is a local web service for building Hongbaoshu `.hbs.zip` packs.

## Current scope

- Create and manage local pack projects
- Import `txt` content
- Normalize chapters, paragraphs, and sentences
- Export sentence CSV for narration production
- Validate pack metadata and book structure
- Build a minimal `.hbs.zip` artifact
- Write logs and JSON reports for import, normalization, export, validation, and build tasks

## Run

```bash
npm install
npm start
```

Open [http://localhost:4310](http://localhost:4310).

## Workspace

Runtime data is stored under `workspace/projects/<projectId>/`.
