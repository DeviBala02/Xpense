import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { "/api": "http://localhost:8080" }, // while developing, /api goes to the Spring Boot backend
  },
  build: {
    // "npm run build" puts the finished website inside the backend, so ONE program serves everything.
    outDir: "../backend/src/main/resources/static",
    emptyOutDir: true,
  },
});
