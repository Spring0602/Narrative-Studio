<script setup lang="ts">
const props=defineProps<{modelValue:Record<string,number>;headers:string[];fields:{key:string;label:string}[];disabled?:boolean}>();
const emit=defineEmits<{ "update:modelValue":[value:Record<string,number>] }>();
function change(key:string,value:unknown) {
  const next={...props.modelValue};
  if(typeof value==="number")next[key]=value;else delete next[key];
  emit("update:modelValue",next);
}
</script>
<template>
  <div class="columns">
    <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
      <el-select :model-value="modelValue[field.key]" clearable filterable :disabled="disabled" placeholder="不导入 / 请选择" @update:model-value="change(field.key,$event)">
        <el-option v-for="(header,index) in headers" :key="index" :label="(index+1)+' · '+(header||'（空表头）')" :value="index" :disabled="!header" />
      </el-select>
    </el-form-item>
  </div>
</template>
<style scoped>.columns{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:0 16px}.el-select{width:100%}</style>
