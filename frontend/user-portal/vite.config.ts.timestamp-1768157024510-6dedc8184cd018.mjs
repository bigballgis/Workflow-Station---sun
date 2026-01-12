// vite.config.ts
import { defineConfig } from "file:///D:/working/Workflow%20Station/Workflow-Station---sun/frontend/user-portal/node_modules/vite/dist/node/index.js";
import vue from "file:///D:/working/Workflow%20Station/Workflow-Station---sun/frontend/user-portal/node_modules/@vitejs/plugin-vue/dist/index.mjs";
import { resolve } from "path";
import AutoImport from "file:///D:/working/Workflow%20Station/Workflow-Station---sun/frontend/user-portal/node_modules/unplugin-auto-import/dist/vite.js";
import Components from "file:///D:/working/Workflow%20Station/Workflow-Station---sun/frontend/user-portal/node_modules/unplugin-vue-components/dist/vite.js";
import { ElementPlusResolver } from "file:///D:/working/Workflow%20Station/Workflow-Station---sun/frontend/user-portal/node_modules/unplugin-vue-components/dist/resolvers.js";
var __vite_injected_original_dirname = "D:\\working\\Workflow Station\\Workflow-Station---sun\\frontend\\user-portal";
var vite_config_default = defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ["vue", "vue-router", "pinia"],
      dts: "src/auto-imports.d.ts"
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: "src/components.d.ts"
    })
  ],
  resolve: {
    alias: {
      "@": resolve(__vite_injected_original_dirname, "src")
    }
  },
  server: {
    port: 3001,
    proxy: {
      "/api/v1/auth": {
        target: "http://localhost:8082",
        changeOrigin: true,
        rewrite: (path) => "/api/portal/auth" + path.substring("/api/v1/auth".length)
      },
      "/api/portal": {
        target: "http://localhost:8082",
        changeOrigin: true
      },
      "/api/v1/admin": {
        target: "http://localhost:8090",
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          "vendor": ["vue", "vue-router", "pinia"],
          "element-plus": ["element-plus"],
          "echarts": ["echarts", "vue-echarts"],
          "bpmn": ["bpmn-js"]
        }
      }
    }
  }
});
export {
  vite_config_default as default
};
//# sourceMappingURL=data:application/json;base64,ewogICJ2ZXJzaW9uIjogMywKICAic291cmNlcyI6IFsidml0ZS5jb25maWcudHMiXSwKICAic291cmNlc0NvbnRlbnQiOiBbImNvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9kaXJuYW1lID0gXCJEOlxcXFx3b3JraW5nXFxcXFdvcmtmbG93IFN0YXRpb25cXFxcV29ya2Zsb3ctU3RhdGlvbi0tLXN1blxcXFxmcm9udGVuZFxcXFx1c2VyLXBvcnRhbFwiO2NvbnN0IF9fdml0ZV9pbmplY3RlZF9vcmlnaW5hbF9maWxlbmFtZSA9IFwiRDpcXFxcd29ya2luZ1xcXFxXb3JrZmxvdyBTdGF0aW9uXFxcXFdvcmtmbG93LVN0YXRpb24tLS1zdW5cXFxcZnJvbnRlbmRcXFxcdXNlci1wb3J0YWxcXFxcdml0ZS5jb25maWcudHNcIjtjb25zdCBfX3ZpdGVfaW5qZWN0ZWRfb3JpZ2luYWxfaW1wb3J0X21ldGFfdXJsID0gXCJmaWxlOi8vL0Q6L3dvcmtpbmcvV29ya2Zsb3clMjBTdGF0aW9uL1dvcmtmbG93LVN0YXRpb24tLS1zdW4vZnJvbnRlbmQvdXNlci1wb3J0YWwvdml0ZS5jb25maWcudHNcIjtpbXBvcnQgeyBkZWZpbmVDb25maWcgfSBmcm9tICd2aXRlJ1xyXG5pbXBvcnQgdnVlIGZyb20gJ0B2aXRlanMvcGx1Z2luLXZ1ZSdcclxuaW1wb3J0IHsgcmVzb2x2ZSB9IGZyb20gJ3BhdGgnXHJcbmltcG9ydCBBdXRvSW1wb3J0IGZyb20gJ3VucGx1Z2luLWF1dG8taW1wb3J0L3ZpdGUnXHJcbmltcG9ydCBDb21wb25lbnRzIGZyb20gJ3VucGx1Z2luLXZ1ZS1jb21wb25lbnRzL3ZpdGUnXHJcbmltcG9ydCB7IEVsZW1lbnRQbHVzUmVzb2x2ZXIgfSBmcm9tICd1bnBsdWdpbi12dWUtY29tcG9uZW50cy9yZXNvbHZlcnMnXHJcblxyXG5leHBvcnQgZGVmYXVsdCBkZWZpbmVDb25maWcoe1xyXG4gIHBsdWdpbnM6IFtcclxuICAgIHZ1ZSgpLFxyXG4gICAgQXV0b0ltcG9ydCh7XHJcbiAgICAgIHJlc29sdmVyczogW0VsZW1lbnRQbHVzUmVzb2x2ZXIoKV0sXHJcbiAgICAgIGltcG9ydHM6IFsndnVlJywgJ3Z1ZS1yb3V0ZXInLCAncGluaWEnXSxcclxuICAgICAgZHRzOiAnc3JjL2F1dG8taW1wb3J0cy5kLnRzJ1xyXG4gICAgfSksXHJcbiAgICBDb21wb25lbnRzKHtcclxuICAgICAgcmVzb2x2ZXJzOiBbRWxlbWVudFBsdXNSZXNvbHZlcigpXSxcclxuICAgICAgZHRzOiAnc3JjL2NvbXBvbmVudHMuZC50cydcclxuICAgIH0pXHJcbiAgXSxcclxuICByZXNvbHZlOiB7XHJcbiAgICBhbGlhczoge1xyXG4gICAgICAnQCc6IHJlc29sdmUoX19kaXJuYW1lLCAnc3JjJylcclxuICAgIH1cclxuICB9LFxyXG4gIHNlcnZlcjoge1xyXG4gICAgcG9ydDogMzAwMSxcclxuICAgIHByb3h5OiB7XHJcbiAgICAgICcvYXBpL3YxL2F1dGgnOiB7XHJcbiAgICAgICAgdGFyZ2V0OiAnaHR0cDovL2xvY2FsaG9zdDo4MDgyJyxcclxuICAgICAgICBjaGFuZ2VPcmlnaW46IHRydWUsXHJcbiAgICAgICAgcmV3cml0ZTogKHBhdGgpID0+ICcvYXBpL3BvcnRhbC9hdXRoJyArIHBhdGguc3Vic3RyaW5nKCcvYXBpL3YxL2F1dGgnLmxlbmd0aClcclxuICAgICAgfSxcclxuICAgICAgJy9hcGkvcG9ydGFsJzoge1xyXG4gICAgICAgIHRhcmdldDogJ2h0dHA6Ly9sb2NhbGhvc3Q6ODA4MicsXHJcbiAgICAgICAgY2hhbmdlT3JpZ2luOiB0cnVlXHJcbiAgICAgIH0sXHJcbiAgICAgICcvYXBpL3YxL2FkbWluJzoge1xyXG4gICAgICAgIHRhcmdldDogJ2h0dHA6Ly9sb2NhbGhvc3Q6ODA5MCcsXHJcbiAgICAgICAgY2hhbmdlT3JpZ2luOiB0cnVlXHJcbiAgICAgIH1cclxuICAgIH1cclxuICB9LFxyXG4gIGJ1aWxkOiB7XHJcbiAgICByb2xsdXBPcHRpb25zOiB7XHJcbiAgICAgIG91dHB1dDoge1xyXG4gICAgICAgIG1hbnVhbENodW5rczoge1xyXG4gICAgICAgICAgJ3ZlbmRvcic6IFsndnVlJywgJ3Z1ZS1yb3V0ZXInLCAncGluaWEnXSxcclxuICAgICAgICAgICdlbGVtZW50LXBsdXMnOiBbJ2VsZW1lbnQtcGx1cyddLFxyXG4gICAgICAgICAgJ2VjaGFydHMnOiBbJ2VjaGFydHMnLCAndnVlLWVjaGFydHMnXSxcclxuICAgICAgICAgICdicG1uJzogWydicG1uLWpzJ11cclxuICAgICAgICB9XHJcbiAgICAgIH1cclxuICAgIH1cclxuICB9XHJcbn0pXHJcbiJdLAogICJtYXBwaW5ncyI6ICI7QUFBcVosU0FBUyxvQkFBb0I7QUFDbGIsT0FBTyxTQUFTO0FBQ2hCLFNBQVMsZUFBZTtBQUN4QixPQUFPLGdCQUFnQjtBQUN2QixPQUFPLGdCQUFnQjtBQUN2QixTQUFTLDJCQUEyQjtBQUxwQyxJQUFNLG1DQUFtQztBQU96QyxJQUFPLHNCQUFRLGFBQWE7QUFBQSxFQUMxQixTQUFTO0FBQUEsSUFDUCxJQUFJO0FBQUEsSUFDSixXQUFXO0FBQUEsTUFDVCxXQUFXLENBQUMsb0JBQW9CLENBQUM7QUFBQSxNQUNqQyxTQUFTLENBQUMsT0FBTyxjQUFjLE9BQU87QUFBQSxNQUN0QyxLQUFLO0FBQUEsSUFDUCxDQUFDO0FBQUEsSUFDRCxXQUFXO0FBQUEsTUFDVCxXQUFXLENBQUMsb0JBQW9CLENBQUM7QUFBQSxNQUNqQyxLQUFLO0FBQUEsSUFDUCxDQUFDO0FBQUEsRUFDSDtBQUFBLEVBQ0EsU0FBUztBQUFBLElBQ1AsT0FBTztBQUFBLE1BQ0wsS0FBSyxRQUFRLGtDQUFXLEtBQUs7QUFBQSxJQUMvQjtBQUFBLEVBQ0Y7QUFBQSxFQUNBLFFBQVE7QUFBQSxJQUNOLE1BQU07QUFBQSxJQUNOLE9BQU87QUFBQSxNQUNMLGdCQUFnQjtBQUFBLFFBQ2QsUUFBUTtBQUFBLFFBQ1IsY0FBYztBQUFBLFFBQ2QsU0FBUyxDQUFDLFNBQVMscUJBQXFCLEtBQUssVUFBVSxlQUFlLE1BQU07QUFBQSxNQUM5RTtBQUFBLE1BQ0EsZUFBZTtBQUFBLFFBQ2IsUUFBUTtBQUFBLFFBQ1IsY0FBYztBQUFBLE1BQ2hCO0FBQUEsTUFDQSxpQkFBaUI7QUFBQSxRQUNmLFFBQVE7QUFBQSxRQUNSLGNBQWM7QUFBQSxNQUNoQjtBQUFBLElBQ0Y7QUFBQSxFQUNGO0FBQUEsRUFDQSxPQUFPO0FBQUEsSUFDTCxlQUFlO0FBQUEsTUFDYixRQUFRO0FBQUEsUUFDTixjQUFjO0FBQUEsVUFDWixVQUFVLENBQUMsT0FBTyxjQUFjLE9BQU87QUFBQSxVQUN2QyxnQkFBZ0IsQ0FBQyxjQUFjO0FBQUEsVUFDL0IsV0FBVyxDQUFDLFdBQVcsYUFBYTtBQUFBLFVBQ3BDLFFBQVEsQ0FBQyxTQUFTO0FBQUEsUUFDcEI7QUFBQSxNQUNGO0FBQUEsSUFDRjtBQUFBLEVBQ0Y7QUFDRixDQUFDOyIsCiAgIm5hbWVzIjogW10KfQo=
