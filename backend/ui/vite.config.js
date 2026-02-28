import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "../static",
    emptyOutDir: true,
  },
  server: {
    proxy: {
      "/upload": "http://localhost:9090",
      "/classify": "http://localhost:9090",
      "/labels": "http://localhost:9090",
      "/images": "http://localhost:9090",
      "/health": "http://localhost:9090",
    },
  },
});
