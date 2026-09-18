import axios from "axios";

export function apiErrorMessage(error: unknown, fallback: string) {
  if (!axios.isAxiosError(error)) return error instanceof Error ? error.message : fallback;
  const message = error.response?.data?.error?.message;
  return typeof message === "string" && message.trim() ? message : fallback;
}
