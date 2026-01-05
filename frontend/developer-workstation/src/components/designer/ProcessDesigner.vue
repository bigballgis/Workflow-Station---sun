<template>
  <div class="process-designer">
    <div class="designer-toolbar">
      <el-button-group>
        <el-button @click="handleZoomIn"><el-icon><ZoomIn /></el-icon></el-button>
        <el-button @click="handleZoomOut"><el-icon><ZoomOut /></el-icon></el-button>
        <el-button @click="handleFitViewport">适应画布</el-button>
      </el-button-group>
      <el-button-group>
        <el-button @click="handleValidate">{{ $t('process.validate') }}</el-button>
        <el-button @click="handleSimulate">{{ $t('process.simulate') }}</el-button>
      </el-button-group>
    </div>
    <div class="designer-content">
      <div class="toolbox">
        <h4>{{ $t('process.toolbox') }}</h4>
        <div class="tool-items">
          <div class="tool-item" draggable="true" @dragstart="handleDragStart('startEvent')">
            <div class="tool-icon start-event"></div>
            <span>开始事件</span>
          </div>
          <div class="tool-item" draggable="true" @dragstart="handleDragStart('endEvent')">
            <div class="tool-icon end-event"></div>
            <span>结束事件</span>
          </div>
          <div class="tool-item" draggable="true" @dragstart="handleDragStart('userTask')">
            <div class="tool-icon user-task"></div>
            <span>用户任务</span>
          </div>
          <div class="tool-item" draggable="true" @dragstart="handleDragStart('exclusiveGateway')">
            <div class="tool-icon gateway"></div>
            <span>排他网关</span>
          </div>
          <div class="tool-item" draggable="true" @dragstart="handleDragStart('parallelGateway')">
            <div class="tool-icon gateway parallel"></div>
            <span>并行网关</span>
          </div>
        </div>
      </div>
      <div ref="canvasRef" class="canvas"></div>
      <div class="properties-panel">
        <h4>{{ $t('process.properties') }}</h4>
        <div v-if="selectedElement" class="property-form">
          <el-form label-position="top" size="small">
            <el-form-item label="ID">
              <el-input v-model="selectedElement.id" disabled />
            </el-form-item>
            <el-form-item label="名称">
              <el-input v-model="selectedElement.name" @change="handlePropertyChange" />
            </el-form-item>
          </el-form>
        </div>
        <div v-else class="no-selection">请选择一个元素</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{ functionUnitId: number }>()

const canvasRef = ref<HTMLElement>()
const selectedElement = ref<{ id: string; name: string } | null>(null)
let bpmnModeler: any = null

function handleDragStart(type: string) {
  // TODO: Implement drag start
}

function handleZoomIn() {
  // TODO: Implement zoom in
}

function handleZoomOut() {
  // TODO: Implement zoom out
}

function handleFitViewport() {
  // TODO: Implement fit viewport
}

function handleValidate() {
  ElMessage.info('流程验证功能开发中')
}

function handleSimulate() {
  ElMessage.info('流程模拟功能开发中')
}

function handlePropertyChange() {
  // TODO: Update element property
}

onMounted(async () => {
  // TODO: Initialize bpmn-js
})

onUnmounted(() => {
  bpmnModeler?.destroy()
})
</script>

<style lang="scss" scoped>
.process-designer {
  height: 600px;
  display: flex;
  flex-direction: column;
}

.designer-toolbar {
  display: flex;
  justify-content: space-between;
  padding: 10px;
  border-bottom: 1px solid #e6e6e6;
}

.designer-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.toolbox {
  width: 200px;
  padding: 10px;
  border-right: 1px solid #e6e6e6;
  overflow-y: auto;
}

.tool-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  cursor: grab;
  
  &:hover {
    background-color: #f5f7fa;
  }
}

.tool-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  
  &.start-event { background-color: #00A651; }
  &.end-event { background-color: #DB0011; }
  &.user-task { background-color: #409EFF; border-radius: 4px; }
  &.gateway { background-color: #FF6600; transform: rotate(45deg); border-radius: 0; }
}

.canvas {
  flex: 1;
  background-color: #fafafa;
}

.properties-panel {
  width: 280px;
  padding: 10px;
  border-left: 1px solid #e6e6e6;
  overflow-y: auto;
}

.no-selection {
  color: #909399;
  text-align: center;
  padding: 20px;
}
</style>
