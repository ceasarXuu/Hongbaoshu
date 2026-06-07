import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AppLayout } from "@/components/layout/AppLayout";
import { ProjectListPage } from "@/components/pages/ProjectListPage";
import { ImportPage } from "@/components/pages/ImportPage";
import { ContentPage } from "@/components/pages/ContentPage";
import { AudioPage } from "@/components/pages/AudioPage";
import { ValidationPage } from "@/components/pages/ValidationPage";
import { BuildPage } from "@/components/pages/BuildPage";

function Layout({ children }: { children: React.ReactNode }) {
  return <AppLayout>{children}</AppLayout>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/"
          element={
            <Layout>
              <ProjectListPage />
            </Layout>
          }
        />
        <Route
          path="/projects/:id/import"
          element={
            <Layout>
              <ImportPage />
            </Layout>
          }
        />
        <Route
          path="/projects/:id/content"
          element={
            <Layout>
              <ContentPage />
            </Layout>
          }
        />
        <Route
          path="/projects/:id/audio"
          element={
            <Layout>
              <AudioPage />
            </Layout>
          }
        />
        <Route
          path="/projects/:id/validate"
          element={
            <Layout>
              <ValidationPage />
            </Layout>
          }
        />
        <Route
          path="/projects/:id/build"
          element={
            <Layout>
              <BuildPage />
            </Layout>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
