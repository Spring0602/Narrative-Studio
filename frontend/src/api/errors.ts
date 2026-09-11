import axios from "axios";

export function apiErrorMessage(error: unknown, fallback: string) {
  if (!axios.isAxiosError(error)) return fallback;
  const message = error.response?.data?.error?.message;
  return typeof message === "string" && message.trim() ? message : fallback;
}
