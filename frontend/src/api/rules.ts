import { http, type ApiEnvelope } from './http'

export type VariableType = 'BOOLEAN' | 'INTEGER' | 'STRING'
export interface VariableInput {
  variableKey: string
  displayName: string
  valueType: VariableType
  initialValue: string
  description?: string | null
}
export interface StateVariable extends VariableInput { id: number; projectId: number }
export interface ConditionInput {
  variableId: number
  operator: 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE'
  expectedValue: string
  conditionGroup: number
}
export interface EffectInput {
  variableId: number
  operation: 'SET' | 'ADD' | 'SUBTRACT'
  operandValue: string
}
export interface RulesInput { conditions: ConditionInput[]; effects: EffectInput[] }
export interface ChoiceRules {
  choiceId: number
  conditions: (ConditionInput & { id: number; sortOrder: number })[]
  effects: (EffectInput & { id: number; sortOrder: number })[]
}
const variablePath = (projectId: number) => `/projects/${projectId}/state-variables`
const rulePath = (projectId: number, nodeId: number, choiceId: number) =>
  `/projects/${projectId}/story-nodes/${nodeId}/choices/${choiceId}/rules`

export async function listVariables(projectId: number) {
  return (await http.get<ApiEnvelope<StateVariable[]>>(variablePath(projectId))).data.data
}
export async function getVariable(projectId: number, variableId: number) {
  return (await http.get<ApiEnvelope<StateVariable>>(`${variablePath(projectId)}/${variableId}`)).data.data
}
export async function createVariable(projectId: number, input: VariableInput) {
  return (await http.post<ApiEnvelope<StateVariable>>(variablePath(projectId), input)).data.data
}
export async function updateVariable(projectId: number, variableId: number, input: VariableInput) {
  return (await http.put<ApiEnvelope<StateVariable>>(`${variablePath(projectId)}/${variableId}`, input)).data.data
}
export async function deleteVariable(projectId: number, variableId: number) {
  await http.delete(`${variablePath(projectId)}/${variableId}`)
}
export async function getChoiceRules(projectId: number, nodeId: number, choiceId: number) {
  return (await http.get<ApiEnvelope<ChoiceRules>>(rulePath(projectId, nodeId, choiceId))).data.data
}
export async function replaceChoiceRules(projectId: number, nodeId: number, choiceId: number, input: RulesInput) {
  return (await http.put<ApiEnvelope<ChoiceRules>>(rulePath(projectId, nodeId, choiceId), input)).data.data
}
