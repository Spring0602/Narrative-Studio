<script setup lang="ts">
import { computed } from "vue";
import { VueFlow,MarkerType,Position } from "@vue-flow/core";
import type { StoryDocument } from "@/api/extensions";
import "@vue-flow/core/dist/style.css";
import "@vue-flow/core/dist/theme-default.css";
const props=defineProps<{document:StoryDocument}>();
const nodes=computed(()=>props.document.nodes.map(n=>({
  id:n.nodeKey,label:n.nodeKey+" · "+n.title,
  position:{x:n.positionX??0,y:n.positionY??0},
  sourcePosition:Position.Right,targetPosition:Position.Left,
  style:{width:"240px",minHeight:"64px",background:n.isStart?"#e6f4ed":n.nodeType==="ENDING"?"#fff1df":"#fff"},
})));
const edges=computed(()=>props.document.choices.map((c,i)=>({
  id:"preview-"+i,source:c.sourceNodeKey,target:c.targetNodeKey,label:c.choiceText,
  type:"smoothstep",markerEnd:MarkerType.ArrowClosed,
})));
</script>
<template><div class="preview-canvas"><VueFlow :nodes="nodes" :edges="edges" :nodes-draggable="false" :nodes-connectable="false" :edges-updatable="false" fit-view-on-init :min-zoom="0.05" /></div></template>
<style scoped>.preview-canvas{height:440px;border:1px solid #dce5df;border-radius:8px;background:#f6f8f5}</style>
